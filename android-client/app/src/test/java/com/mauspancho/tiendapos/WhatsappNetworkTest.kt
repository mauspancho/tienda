package com.mauspancho.tiendapos

import com.mauspancho.tiendapos.data.api.TiendaApi
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.data.repository.RemoteWhatsappRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.*
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import org.junit.*
import org.junit.Assert.*

class WhatsappNetworkTest {
    private val server = MockWebServer(); private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var repo: RemoteWhatsappRepository
    @Before fun setup() { server.start(); repo = RemoteWhatsappRepository(Retrofit.Builder().baseUrl(server.url("/"))
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(TiendaApi::class.java)) }
    @After fun cleanup() { server.shutdown() }
    @Test fun apiUsesMobileJwtPathsAndPromotionKeyWithoutAutomationCredentials() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(json.encodeToString(whatsappFixture())))
        assertEquals("CONNECTED", repo.state().connection.status); assertEquals("/api/v1/whatsapp", server.takeRequest().path)
        val attempt = PromotionAttempt("fixed-request-key-123", 5, "Arroz", PromotionRequest("Oferta", true, "+5215550000000"))
        repeat(2) {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(json.encodeToString(PromotionResult("COMPLETED", 1, 0, it > 0, false, emptyList()))))
            repo.send(attempt)
            val request = server.takeRequest(); assertEquals("/api/v1/whatsapp/products/5/promotions", request.path)
            assertEquals(attempt.key, request.getHeader("Idempotency-Key")); assertEquals(attempt.request, json.decodeFromString<PromotionRequest>(request.body.readUtf8()))
        }
    }
    @Test fun selectionSendsOnlyStableIdsAndActionRejectsUnknownRoutes() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(json.encodeToString(whatsappFixture())))
        repo.groups(listOf(7)); val request = server.takeRequest()
        assertEquals("PUT", request.method); assertEquals("/api/v1/whatsapp/announcements/groups", request.path)
        assertEquals(listOf(7L), json.decodeFromString<WhatsappSelection>(request.body.readUtf8()).groupIds)
        assertTrue(runCatching { repo.action("../../admin") }.isFailure); assertEquals(1, server.requestCount)
    }
    @Test fun productPromotionFilterIsAppliedByServerAcrossPages() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(json.encodeToString(Page<Product>(emptyList(), 2, 20, 0, 0))))
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(TiendaApi::class.java)
        api.products("arroz", 2, active = true, whatsapp = true)
        val url = server.takeRequest().requestUrl!!
        assertEquals("true", url.queryParameter("whatsapp")); assertEquals("2", url.queryParameter("page"))
        assertEquals("arroz", url.queryParameter("q")); assertEquals("true", url.queryParameter("active"))
    }
    @Test fun qrRejectsWrongMediaTypeAndOversizedStreamingResponse() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("not a QR"))
        assertTrue(runCatching { repo.qr() }.exceptionOrNull() is IllegalArgumentException)
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(ByteArray(2 * 1024 * 1024 + 1))))
        assertTrue(runCatching { repo.qr() }.exceptionOrNull() is IllegalArgumentException)
    }
}
