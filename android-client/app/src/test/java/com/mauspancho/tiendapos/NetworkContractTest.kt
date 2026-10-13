package com.mauspancho.tiendapos

import com.mauspancho.tiendapos.data.api.*
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.data.repository.*
import com.mauspancho.tiendapos.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.*
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class NetworkContractTest {
    @Test fun lateUnauthorizedRequestCannotRefreshOrBorrowAnotherAccountsCredentials() {
        val manager = SessionManager("https://store.example/", MemoryStore()).apply { save(session()) }
        val original = Request.Builder().url(server.url("/api/v1/auth/me"))
            .header("Authorization", "Bearer access").tag(SessionOwner::class.java, SessionOwner(1, "fixture")).build()
        val next = session("other-access").let { it.copy(user = it.user.copy(id = 2, username = "other-user")) }
        manager.save(next)
        val failed = Response.Builder().request(original).protocol(Protocol.HTTP_1_1).code(401).message("Revoked").build()
        val authenticator = SessionAuthenticator(manager, retrofit().create(AuthApi::class.java))
        assertNull(authenticator.authenticate(null, failed))
        assertEquals(0, server.requestCount)
        assertEquals("other-access", manager.state.value?.accessToken)
    }
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }
    private fun retrofit(client: OkHttpClient = OkHttpClient()) = Retrofit.Builder().baseUrl(server.url("/"))
        .client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
    private fun response(body: String, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
    @Test fun productRepositoryUsesPagedRealContract() = runTest {
        server.enqueue(response(json.encodeToString(Page(listOf(product()), 0, 20, 1, 1))))
        val values = RemoteProductRepository(retrofit().create(TiendaApi::class.java)).search("café")
        assertEquals("Producto 1", values.single().name)
        val request = server.takeRequest()
        assertEquals("/api/v1/products", request.requestUrl!!.encodedPath)
        assertEquals("café", request.requestUrl!!.queryParameter("q")); assertEquals("20", request.requestUrl!!.queryParameter("size"))
        assertEquals("true", request.requestUrl!!.queryParameter("active"))
    }
    @Test fun checkoutUsesSameIdempotencyAndExactDecimals() = runTest {
        server.enqueue(response("""{"status":409,"error":"CONFLICT","message":"Solicitud en conflicto","fieldErrors":{}}""", 409))
        server.enqueue(response("""{"status":409,"error":"CONFLICT","message":"Solicitud en conflicto","fieldErrors":{}}""", 409))
        val repository = RemotePosRepository(retrofit().create(TiendaApi::class.java))
        val attempt = CheckoutAttempt.create(Cart().add(product(price = "123456789.123456789")), "CASH", BigDecimal("123456789.123456789"))
        repeat(2) { try { repository.checkout(attempt); fail() } catch (_: HttpException) { } }
        val a = server.takeRequest(); val b = server.takeRequest()
        assertEquals("/api/v1/pos/checkout", a.path); assertEquals(attempt.key, a.getHeader("Idempotency-Key")); assertEquals(a.getHeader("Idempotency-Key"), b.getHeader("Idempotency-Key"))
        val body = a.body.readUtf8(); assertEquals(body, b.body.readUtf8())
        assertEquals(BigDecimal("123456789.123456789"), json.decodeFromString<CheckoutRequest>(body).receivedAmount)
    }
    @Test fun authenticatorRenewsBearerAndStopsAfterOneRetry() {
        val store = MemoryStore(); val manager = SessionManager(server.url("/").toString(), store).apply { save(session()) }
        val auth = retrofit().create(AuthApi::class.java)
        val authenticator = SessionAuthenticator(manager, auth)
        val request = Request.Builder().url(server.url("/api/v1/dashboard")).header("Authorization", "Bearer access").build()
        val unauthorized = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized").build()
        server.enqueue(response(json.encodeToString(session("new-access", "new-refresh"))))
        val retried = authenticator.authenticate(null, unauthorized)!!
        assertEquals("Bearer new-access", retried.header("Authorization")); assertEquals("new-refresh", manager.state.value?.refreshToken)
        assertEquals("/api/v1/auth/refresh", server.takeRequest().path)
        val second = unauthorized.newBuilder().request(retried).priorResponse(unauthorized).build()
        assertNull(authenticator.authenticate(null, second)); assertEquals(1, server.requestCount)
        assertNull(manager.state.value)
    }
    @Test fun refresh401InvalidatesBut503PreservesSession() {
        val manager = SessionManager(server.url("/").toString(), MemoryStore()).apply { save(session()) }
        val authenticator = SessionAuthenticator(manager, retrofit().create(AuthApi::class.java))
        val response = Response.Builder().request(Request.Builder().url(server.url("/api/v1/auth/me")).header("Authorization", "Bearer access").build())
            .protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized").build()
        server.enqueue(this.response("{}", 503)); assertNull(authenticator.authenticate(null, response)); assertNotNull(manager.state.value)
        server.enqueue(this.response("{}", 401)); assertNull(authenticator.authenticate(null, response)); assertNull(manager.state.value)
    }
    @Test fun errorsAreMappedWithoutRawExceptionsOrServerInternals() {
        assertTrue(UnknownHostException("secret details").failure(json).message.contains("DNS"))
        assertTrue(SSLHandshakeException("internal certificate").failure(json).message.contains("certificado"))
        for (status in listOf(401, 403, 404, 409, 422, 500)) {
            val raw = """{"status":$status,"error":"ERROR","message":"java.lang.Exception: secret","fieldErrors":{}}"""
            val http = HttpException(retrofit2.Response.error<Unit>(status, raw.toResponseBody("application/json".toMediaType())))
            val failure = http.failure(json); assertEquals(status, failure.status); assertFalse(failure.message.contains("java.lang"))
        }
    }
    @Test fun decimalRoundTripPreservesFullPrecision() {
        val original = product(price = "123456789.123456789123456789")
        val restored = json.decodeFromString<Product>(json.encodeToString(original))
        assertEquals(0, original.salePrice.compareTo(restored.salePrice))
    }
    @Test fun stockAndCapitalDetailsUseOnlyAvailableEndpoints() = runTest {
        val repository = ModuleRepository(retrofit().create(TiendaApi::class.java), json)
        server.enqueue(response(json.encodeToString(product())))
        repository.detail("inventory/stock", buildJsonObject { put("id", 1) })
        assertEquals("/api/v1/products/1", server.takeRequest().path)
        val capital = buildJsonObject { put("id", 2); put("amount", 10) }
        assertEquals(capital, repository.detail("finances/capital", capital)); assertEquals(1, server.requestCount)
    }
    @Test fun productEditReadsLatestStockBeforeSubmitting() = runTest {
        val repository = ModuleRepository(retrofit().create(TiendaApi::class.java), json)
        server.enqueue(response(json.encodeToString(product().copy(currentStock = BigDecimal("17")))))
        server.enqueue(response(json.encodeToString(product())))
        val source = json.encodeToJsonElement(product()).jsonObject
        val spec = ModuleForms.edit("products", "1", source)!!
        repository.submit(spec, ModuleForms.payload(spec, spec.fields.associate { it.key to it.initial }, source), null)
        assertEquals("GET", server.takeRequest().method)
        val sent = server.takeRequest(); assertEquals("PUT", sent.method)
        assertEquals("17", json.parseToJsonElement(sent.body.readUtf8()).jsonObject.getValue("currentStock").jsonPrimitive.content)
    }
}
