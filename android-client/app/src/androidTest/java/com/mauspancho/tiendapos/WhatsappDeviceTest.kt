package com.mauspancho.tiendapos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mauspancho.tiendapos.data.dto.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

/** Actual Activity and authenticated HTTP transport; all messages terminate in a disposable fixture. */
class WhatsappDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun awaitText(text: String) = compose.waitUntil(15000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun scroll(tag: String, text: String) { compose.onNodeWithTag(tag).performScrollToNode(hasText(text)) }
    @Test fun adminConnectsSelectsGroupsAndReviewsIndividualPromotion() {
        Assume.assumeTrue(BuildConfig.LAN_HOST == "10.0.2.15")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TiendaApplication
        val server = MockWebServer()
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        val png = java.io.ByteArrayOutputStream().use { output ->
            val bitmap = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLACK)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle(); output.toByteArray()
        }
        val zero = BigDecimal.ZERO
        val user = User(1, "fixture", "Usuario de prueba", enabled = true, roles = listOf("ADMIN"))
        val sample = Product(1, "P1", "1234567890123", "Agua de prueba", purchaseCost = BigDecimal("5"), salePrice = BigDecimal("12.30"),
            currentStock = BigDecimal("25"), minimumStock = BigDecimal("2"), unit = "PIEZA", tax = zero, active = true, promoted = false, promocionWhatsapp = true, lowStock = false)
        var status = "NOT_CONFIGURED"; var synced = false; var selected = false; var enabled = false
        fun state() = WhatsappState(WhatsappConnection(true, "Fixture", "ACTIVE", status),
            WhatsappAnnouncements(if (enabled) "LISTO" else "DESHABILITADO", enabled, if (synced) 1 else 0, if (selected) 1 else 0, if (selected) 1 else 0, enabled),
            if (synced) listOf(WhatsappGroup(7, "Clientes", true, selected)) else emptyList(),
            WhatsappSync(if (synced) "COMPLETED" else "IDLE", false, if (synced) 1 else 0))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                val body = when (request.requestUrl!!.encodedPath) {
                    "/api/v1/auth/login", "/api/v1/auth/refresh" -> app.json.encodeToString(Session("fixture-access", "fixture-refresh", "Bearer", 900, user))
                    "/api/v1/auth/me" -> app.json.encodeToString(user)
                    "/api/v1/dashboard" -> app.json.encodeToString(Dashboard(zero, zero, zero, 0, 0, zero, zero, emptyList(), emptyList()))
                    "/api/v1/whatsapp" -> app.json.encodeToString(state())
                    "/api/v1/whatsapp/connect" -> { status = "WAITING_QR"; app.json.encodeToString(state()) }
                    "/api/v1/whatsapp/qr" -> return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(png))
                    "/api/v1/whatsapp/refresh" -> { status = "CONNECTED"; app.json.encodeToString(state()) }
                    "/api/v1/whatsapp/groups/sync" -> {
                        if (request.method == "POST") app.json.encodeToString(WhatsappSync("RUNNING", true, 0))
                        else { synced = true; app.json.encodeToString(WhatsappSync("COMPLETED", false, 1)) }
                    }
                    "/api/v1/whatsapp/announcements/groups" -> { selected = app.json.decodeFromString<WhatsappSelection>(request.body.clone().readUtf8()).groupIds == listOf(7L); app.json.encodeToString(state()) }
                    "/api/v1/whatsapp/announcements/enabled" -> { enabled = true; app.json.encodeToString(state()) }
                    "/api/v1/products" -> app.json.encodeToString(Page(listOf(sample), 0, 20, 1, 1))
                    "/api/v1/products/1" -> app.json.encodeToString(sample)
                    "/api/v1/whatsapp/products/1/preview" -> app.json.encodeToString(PromotionPreview(true, true, suggestedMessage = "Oferta de prueba", groupNames = listOf("Clientes"), groupCount = 1))
                    "/api/v1/whatsapp/products/1/promotions" -> app.json.encodeToString(PromotionResult("COMPLETED", 1, 0, false, false, listOf(PromotionDelivery("Número de prueba", "ACCEPTED"))))
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start(InetAddress.getByName("0.0.0.0"), 0)
        var activity: ActivityScenario<MainActivity>? = null
        try {
            runBlocking { app.vault.clear(); app.preferences.server("http://10.0.2.15:${server.port}/", "0.1.0", "v1") }
            activity = ActivityScenario.launch(MainActivity::class.java)
            awaitText("INICIAR SESIÓN")
            compose.onNodeWithText("Usuario").performTextInput("fixture"); compose.onNodeWithText("Contraseña").performTextInput("test-only")
            compose.onNodeWithText("INICIAR SESIÓN").performClick(); awaitText("Hola, Usuario de prueba")
            compose.onNodeWithText("Menú").performClick(); scroll("drawer.list", "WhatsApp"); compose.onNodeWithText("WhatsApp").performClick()
            awaitText("Conectar WhatsApp"); compose.onNodeWithText("Conectar WhatsApp").performClick(); compose.onNodeWithText("Confirmar").performClick()
            awaitText("Mostrar QR"); scroll("whatsapp.list", "Mostrar QR"); compose.onNodeWithText("Mostrar QR").performClick()
            compose.waitUntil(15000) { requests.any { it.requestUrl!!.encodedPath == "/api/v1/whatsapp/qr" } }
            compose.waitForIdle(); scroll("whatsapp.list", "Ocultar QR")
            compose.onNodeWithContentDescription("QR para vincular WhatsApp").assertExists()
            scroll("whatsapp.list", "Actualizar conexión"); compose.onNodeWithText("Actualizar conexión").performClick(); awaitText("Conectado")
            compose.onNodeWithContentDescription("QR para vincular WhatsApp").assertDoesNotExist()
            scroll("whatsapp.list", "Sincronizar grupos"); compose.onNodeWithText("Sincronizar grupos").performClick(); awaitText("Clientes")
            scroll("whatsapp.list", "Clientes"); compose.onNodeWithText("Clientes").performClick()
            scroll("whatsapp.list", "Guardar grupos para anuncios"); compose.onNodeWithText("Guardar grupos para anuncios").performClick(); compose.onNodeWithText("Confirmar").performClick()
            compose.waitUntil(10000) { selected }
            scroll("whatsapp.list", "Habilitar anuncios"); compose.onNodeWithText("Habilitar anuncios").performClick(); compose.onNodeWithText("Confirmar").performClick()
            awaitText("Anuncios automáticos: Listo")
            compose.onNodeWithText("Menú").performClick(); scroll("drawer.list", "Productos")
            compose.onNode(hasText("Productos") and hasAnyAncestor(hasTestTag("drawer.list"))).performClick()
            awaitText("Agua de prueba"); compose.onNodeWithText("Agua de prueba").performClick()
            scroll("product.detail.list", "Enviar promoción por WhatsApp"); compose.onNodeWithText("Enviar promoción por WhatsApp").performClick()
            awaitText("Número individual"); compose.onNodeWithText("Número individual").performClick()
            compose.onNodeWithText("Número con código de país").performTextInput("5215550000000")
            scroll("promotion.list", "Revisar promoción"); compose.onNodeWithText("Revisar promoción").performClick(); compose.onNodeWithText("Cancelar").performClick()
            assertFalse(requests.any { it.requestUrl!!.encodedPath.endsWith("/promotions") })
            scroll("promotion.list", "Revisar promoción"); compose.onNodeWithText("Revisar promoción").performClick(); compose.onNodeWithText("Confirmar envío").performClick()
            awaitText("Resultado de la promoción")
            val sent = requests.single { it.requestUrl!!.encodedPath.endsWith("/promotions") }
            assertEquals("Bearer fixture-access", sent.getHeader("Authorization")); assertFalse(sent.getHeader("Idempotency-Key").isNullOrBlank())
            assertEquals(PromotionRequest("Oferta de prueba", true, "5215550000000"), app.json.decodeFromString<PromotionRequest>(sent.body.readUtf8()))
        } finally { activity?.close(); runBlocking { app.vault.clear(); app.preferences.forgetServer() }; server.shutdown() }
    }
}
