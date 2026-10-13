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
import java.net.InetAddress
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList

/** Real Activity + DataStore + Keystore + Retrofit, using a disposable server fixture on the emulator itself. */
class FullFlowDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun loginNavigateCheckoutTicketAndLogoutUsingRealTransport() {
        Assume.assumeTrue("Ejecutar con -PlanHost=10.0.2.15 en el emulador estándar", BuildConfig.LAN_HOST == "10.0.2.15")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TiendaApplication
        val server = MockWebServer()
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        val zero = BigDecimal.ZERO
        val sample = Product(1, "P1", "1234567890123", "Agua de prueba", purchaseCost = BigDecimal("5"), salePrice = BigDecimal("12.30"),
            currentStock = BigDecimal("25"), minimumStock = BigDecimal("2"), unit = "PIEZA", tax = zero, active = true, promoted = false, promocionWhatsapp = false, lowStock = false)
        val user = User(1, "fixture", "Usuario de prueba", enabled = true, roles = listOf("ADMIN"))
        val sale = Sale(1, "TEST-001", "2026-10-01T12:00:00", Ref(1, "Usuario de prueba"), null,
            BigDecimal("12.30"), zero, zero, BigDecimal("12.30"), "COMPLETED", Payment("CASH", BigDecimal("12.30"), BigDecimal("12.30"), zero),
            listOf(SaleLine(1, sample.name, BigDecimal.ONE, sample.salePrice, sample.purchaseCost, zero, sample.salePrice, BigDecimal("7.30"))))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                val path = request.requestUrl!!.encodedPath
                val body = when (path) {
                    "/api/v1/health" -> app.json.encodeToString(Health("UP", "Tienda POS", "v1", "0.1.0"))
                    "/api/v1/auth/login", "/api/v1/auth/refresh" -> app.json.encodeToString(Session("fixture-access", "fixture-refresh", "Bearer", 900, user))
                    "/api/v1/auth/me" -> app.json.encodeToString(user)
                    "/api/v1/dashboard" -> app.json.encodeToString(Dashboard(zero, zero, zero, 0, 0, zero, zero, emptyList(), emptyList()))
                    "/api/v1/pos" -> app.json.encodeToString(PosState(true, PosCash(1, "2026-10-01T12:00:00", zero), listOf("CASH", "CARD", "TRANSFER")))
                    "/api/v1/products" -> app.json.encodeToString(Page(listOf(sample), 0, 20, 1, 1))
                    "/api/v1/pos/checkout", "/api/v1/tickets/TEST-001" -> app.json.encodeToString(sale)
                    "/api/v1/auth/logout" -> return MockResponse().setResponseCode(204)
                    else -> return MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json").setBody("{\"status\":404,\"error\":\"NOT_FOUND\",\"message\":\"Sin datos\"}")
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start(InetAddress.getByName("0.0.0.0"), 0)
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            runBlocking { app.vault.clear(); app.preferences.server("http://10.0.2.15:${server.port}/", "0.1.0", "v1") }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(10000) { compose.onAllNodesWithText("INICIAR SESIÓN").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Usuario").performTextInput("fixture"); compose.onNodeWithText("Contraseña").performTextInput("test-only")
            compose.onNodeWithText("INICIAR SESIÓN").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Hola, Usuario de prueba").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("bottom.POS").performClick()
            compose.onNodeWithText("Buscar nombre, marca o código").performTextInput("Agua")
            compose.waitUntil(10000) { compose.onAllNodesWithText("Agua de prueba").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Agua de prueba").performClick()
            compose.onNodeWithText("Buscar nombre, marca o código").assertTextEquals("Buscar nombre, marca o código", "")
            compose.onNodeWithText("Buscar nombre, marca o código").performTextInput("Agua")
            compose.onNodeWithText("Buscar nombre, marca o código").assertTextContains("Agua")
            compose.onNodeWithText("Buscar nombre, marca o código").performTextClearance()
            compose.onNodeWithTag("pos.list").performScrollToNode(hasText("Cobrar"))
            compose.onNodeWithText("Importe recibido").assertDoesNotExist()
            compose.onNodeWithText("Método de pago").assertDoesNotExist()
            compose.onNodeWithText("Cobrar").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Venta realizada").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Ver ticket").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("TEST-001").fetchSemanticsNodes().isNotEmpty() }
            val checkout = requests.single { it.requestUrl!!.encodedPath == "/api/v1/pos/checkout" }
            assertNotNull(checkout.getHeader("Idempotency-Key")); assertEquals("Bearer fixture-access", checkout.getHeader("Authorization"))
            val body = app.json.decodeFromString<CheckoutRequest>(checkout.body.readUtf8())
            assertEquals("CASH", body.paymentMethod)
            assertEquals(BigDecimal("12.30"), body.receivedAmount)
            compose.onNodeWithText("Menú").performClick()
            compose.onNodeWithTag("drawer.list").performScrollToNode(hasText("Configuración"))
            compose.onNodeWithText("Configuración").performClick()
            compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
            compose.onNodeWithText("Continuar").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("INICIAR SESIÓN").fetchSemanticsNodes().isNotEmpty() }
            assertNull(app.vault.read())
        } finally { scenario?.close(); runBlocking { app.vault.clear(); app.preferences.forgetServer() }; server.shutdown() }
    }
}
