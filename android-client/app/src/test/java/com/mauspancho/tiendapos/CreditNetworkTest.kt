package com.mauspancho.tiendapos

import com.mauspancho.tiendapos.data.api.TiendaApi
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.data.repository.RemoteCreditRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.*
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal

class CreditNetworkTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var repo: RemoteCreditRepository
    @Before fun start() {
        server.start()
        repo = RemoteCreditRepository(Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(TiendaApi::class.java))
    }
    @After fun stop() { server.shutdown() }
    private fun response(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    @Test fun currentStatementReadsManualChargesWithoutRecalculatingServerDebt() = runTest {
        val charge = CreditCharge(33, "2026-10-13T10:00:00", BigDecimal("50.00"), "Saldo inicial", "ACTIVE", "fixture", BigDecimal("10.00"), BigDecimal("40.00"))
        val source = creditAccount().copy(charges = listOf(charge))
        server.enqueue(response(json.encodeToString(source)))
        val account = repo.statement(7)
        assertEquals(charge, account.charges.single())
        assertEquals(BigDecimal("100.00"), account.customer.balance)
        val old = json.encodeToJsonElement(source).jsonObject.let { JsonObject(it - "charges") }
        assertTrue(json.decodeFromJsonElement<CreditStatement>(old).charges.isEmpty())
    }
    @Test fun pagedBalancesAndStatementUseVerifiedServerContract() = runTest {
        server.enqueue(response(json.encodeToString(Page(listOf(creditAccount().customer), 1, 20, 21, 2))))
        val page = repo.customers("Ana López", "WITHOUT_DEBT", 1)
        assertEquals(21L, page.totalElements)
        val request = server.takeRequest()
        assertEquals("/api/v1/credits/customers", request.requestUrl!!.encodedPath)
        assertEquals("WITHOUT_DEBT", request.requestUrl!!.queryParameter("filter"))
        assertEquals("Ana López", request.requestUrl!!.queryParameter("q"))
        assertEquals("1", request.requestUrl!!.queryParameter("page"))
        server.enqueue(response(json.encodeToString(creditAccount())))
        val account = repo.statement(7)
        assertEquals("/api/v1/credits/customers/7", server.takeRequest().requestUrl!!.encodedPath)
        assertEquals(BigDecimal("100.00"), account.customer.balance)
        assertEquals("Nombre histórico", account.credits.single().items.single().product)
    }
    @Test fun paymentPostsSameIdempotencyKeyAndBodyToStableCustomer() = runTest {
        val attempt = CreditPaymentAttempt("fixed-key", 7, CreditPaymentRequest(BigDecimal("12.30"), "TRANSFER", "Abono"))
        val posts = mutableListOf<RecordedRequest>()
        repeat(2) {
            server.enqueue(response("""{"cashOpen":true,"paymentMethods":["CASH","CARD","TRANSFER"],"settlementTypes":["IMMEDIATE","CREDIT"]}"""))
            server.enqueue(response("""{"id":99,"amount":12.30,"method":"TRANSFER","customerBalance":87.70,"createdAt":"2026-10-08T12:00:00"}"""))
            assertEquals(BigDecimal("87.70"), repo.payment(attempt).customerBalance)
            assertEquals("/api/v1/pos", server.takeRequest().requestUrl!!.encodedPath)
            posts.add(server.takeRequest())
        }
        assertEquals("/api/v1/credits/customers/7/payments", posts[0].requestUrl!!.encodedPath)
        assertEquals("fixed-key", posts[0].getHeader("Idempotency-Key"))
        assertEquals(posts[0].getHeader("Idempotency-Key"), posts[1].getHeader("Idempotency-Key"))
        val first = posts[0].body.readUtf8(); assertEquals(first, posts[1].body.readUtf8())
        assertEquals(attempt.request, json.decodeFromString<CreditPaymentRequest>(first))
        assertFalse(first.contains("stock")); assertFalse(first.contains("productId"))
    }
    @Test fun unsupportedServerStopsPaymentBeforePost() = runTest {
        server.enqueue(response("""{"cashOpen":true,"paymentMethods":["CASH"]}"""))
        assertTrue(runCatching { repo.payment(CreditPaymentAttempt("key", 7, CreditPaymentRequest(BigDecimal.ONE, "CASH"))) }.isFailure)
        assertEquals(1, server.requestCount)
    }
}
