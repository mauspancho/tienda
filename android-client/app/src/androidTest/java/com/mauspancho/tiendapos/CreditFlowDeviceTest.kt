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

class CreditFlowDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun nativeCustomerCreditPaymentAndHistoricalStatementUseRealTransport() {
        Assume.assumeTrue(BuildConfig.LAN_HOST == "10.0.2.15")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TiendaApplication
        val server = MockWebServer(); val requests = CopyOnWriteArrayList<RecordedRequest>()
        val zero = BigDecimal("0.00"); val price = BigDecimal("12.30"); val initial = BigDecimal("20.00")
        val user = User(1, "fixture", "Usuario de prueba", enabled = true, roles = listOf("ADMIN"))
        val ana = Customer(7, "Ana", "López", "Ana López", active = true)
        var created: Customer? = null; var issued = false; var paid = false
        val sample = Product(1, "P1", "1234567890123", "Agua de prueba", purchaseCost = BigDecimal("5"), salePrice = price,
            currentStock = BigDecimal("25"), minimumStock = BigDecimal("2"), unit = "PIEZA", tax = zero, active = true, promoted = false, promocionWhatsapp = false, lowStock = false)
        fun account(id: Long): CreditStatement {
            val customer = if (id == 7L) ana else created!!
            val total = if (id == 7L) initial + if (issued) price else zero else zero
            val balance = if (id == 7L && paid) zero else total
            val row = CreditCustomer(id, customer.name, customer.lastName, customer.fullName, customer.phone, true, balance, total, if (paid && id == 7L) total else zero, "2026-10-08T12:00:00")
            val sales = if (id == 7L) buildList {
                add(CreditSale(10, "HIST-001", "2026-10-07T10:00:00", initial, if (paid) initial else zero, if (paid) zero else initial, "COMPLETED",
                    listOf(CreditSaleLine("Arroz histórico", BigDecimal("2"), BigDecimal("10.00"), initial))))
                if (issued) add(CreditSale(11, "C-TEST", "2026-10-08T11:00:00", price, if (paid) price else zero, if (paid) zero else price, "COMPLETED",
                    listOf(CreditSaleLine(sample.name, BigDecimal.ONE, price, price))))
            } else emptyList()
            val movements = if (id == 7L) buildList {
                if (paid) add(CreditMovement("2026-10-08T12:00:00", "ABONO", "AB-99", "Liquidación", zero, total, zero, "fixture", false, 99))
                if (issued) add(CreditMovement("2026-10-08T11:00:00", "CRÉDITO", "C-TEST", sample.name, price, zero, total, "fixture", false, 11))
                add(CreditMovement("2026-10-07T10:00:00", "CRÉDITO", "HIST-001", "Arroz histórico", initial, zero, initial, "fixture", false, 10))
            } else emptyList()
            return CreditStatement(row, sales, if (paid && id == 7L) listOf(CreditPayment(99, "2026-10-08T12:00:00", total, "CASH", null, "ACTIVE", "fixture")) else emptyList(), movements)
        }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request); val path = request.requestUrl!!.encodedPath
                val body = when {
                    path == "/api/v1/auth/login" || path == "/api/v1/auth/refresh" -> app.json.encodeToString(Session("fixture-access", "fixture-refresh", "Bearer", 900, user))
                    path == "/api/v1/auth/me" -> app.json.encodeToString(user)
                    path == "/api/v1/dashboard" -> app.json.encodeToString(Dashboard(zero, zero, zero, 0, 0, zero, zero, emptyList(), emptyList()))
                    path == "/api/v1/pos" -> app.json.encodeToString(PosState(true, paymentMethods = listOf("CASH", "CARD", "TRANSFER"), settlementTypes = listOf("IMMEDIATE", "CREDIT")))
                    path == "/api/v1/credits/summary" -> app.json.encodeToString(CreditSummary(account(7).customer.balance, if (paid) 0 else 1, account(7).customer.totalGranted, account(7).customer.totalPaid))
                    path == "/api/v1/credits/customers" -> {
                        val rows = listOfNotNull(account(7).customer, created?.let { account(it.id).customer }).filter { request.requestUrl!!.queryParameter("filter") != "DEBT" || it.balance.signum() > 0 }
                        app.json.encodeToString(Page(rows, 0, 20, rows.size.toLong(), 1))
                    }
                    path == "/api/v1/customers" && request.method == "POST" -> {
                        val input = app.json.decodeFromString<CustomerRequest>(request.body.clone().readUtf8())
                        created = Customer(8, input.name, input.lastName, input.name, input.phone, input.notes, true)
                        app.json.encodeToString(created!!)
                    }
                    path == "/api/v1/customers" -> {
                        val q = request.requestUrl!!.queryParameter("q").orEmpty()
                        val rows = listOfNotNull(ana, created).filter { q.isBlank() || it.fullName.contains(q, true) }
                        app.json.encodeToString(Page(rows, 0, 20, rows.size.toLong(), 1))
                    }
                    path == "/api/v1/credits/customers/7" -> app.json.encodeToString(account(7))
                    path == "/api/v1/credits/customers/8" -> app.json.encodeToString(account(8))
                    path == "/api/v1/products" -> app.json.encodeToString(Page(listOf(sample), 0, 20, 1, 1))
                    path == "/api/v1/pos/checkout" -> {
                        issued = true
                        app.json.encodeToString(Sale(11, "C-TEST", "2026-10-08T11:00:00", Ref(1, "fixture"), Ref(7, "Ana López"), price, zero, zero, price, "COMPLETED", null,
                            listOf(SaleLine(1, sample.name, BigDecimal.ONE, price, sample.purchaseCost, zero, price, BigDecimal("7.30"))), "CREDIT", zero, price))
                    }
                    path == "/api/v1/credits/customers/7/payments" -> { paid = true; app.json.encodeToString(CreditPaymentResult(99, initial + price, "CASH", zero, "2026-10-08T12:00:00")) }
                    else -> return MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json").setBody("{\"status\":404,\"error\":\"NOT_FOUND\",\"message\":\"Sin datos\"}")
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start(InetAddress.getByName("0.0.0.0"), 0)
        var scenario: ActivityScenario<MainActivity>? = null
        fun scroll(text: String, list: String = "credits.list") { compose.onNodeWithTag(list).performScrollToNode(hasText(text)) }
        fun waitText(text: String) { compose.waitUntil(10000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        try {
            runBlocking { app.vault.clear(); app.preferences.server("http://10.0.2.15:${server.port}/", "0.1.0", "v1") }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitText("INICIAR SESIÓN")
            compose.onNodeWithText("Usuario").performTextInput("fixture"); compose.onNodeWithText("Contraseña").performTextInput("test-only"); compose.onNodeWithText("INICIAR SESIÓN").performClick()
            waitText("Hola, Usuario de prueba"); compose.onNodeWithText("Menú").performClick()
            compose.onNodeWithTag("drawer.list").performScrollToNode(hasText("Créditos")); compose.onNodeWithText("Créditos").performClick()
            waitText("Registrar cliente"); compose.onNodeWithText("Registrar cliente").performClick()
            compose.onNodeWithText("Nombre *").performTextInput("Beto")
            scroll("Guardar cliente", "customer.editor.list"); compose.onNodeWithText("Guardar cliente").performClick(); compose.onNodeWithText("Confirmar").performClick()
            waitText("Beto"); compose.onNodeWithText("Volver a clientes").performClick()
            waitText("Ana López"); compose.onNodeWithText("Ana López").performClick()
            scroll("Nuevo crédito"); compose.onNodeWithText("Nuevo crédito").performClick()
            waitText("Buscar nombre, marca o código"); compose.onNodeWithText("Buscar nombre, marca o código").performTextInput("Agua")
            waitText("Agua de prueba"); compose.onNodeWithText("Agua de prueba").performClick()
            scroll("Confirmar crédito", "pos.list"); compose.onNodeWithText("Confirmar crédito").performClick(); compose.onNodeWithText("Confirmar").performClick()
            waitText("Crédito registrado"); waitText("Saldo actualizado del cliente $32.30 MXN")
            compose.onNodeWithText("Volver a Créditos").performClick()
            scroll("Registrar abono"); compose.onNodeWithText("Registrar abono").performClick()
            compose.onNodeWithText("Cantidad a abonar").performTextInput("32.30")
            scroll("Confirmar abono", "credit.payment.list"); compose.onNodeWithText("Confirmar abono").performClick(); compose.onNodeWithText("Confirmar").performClick()
            waitText("Abono registrado · AB-99"); compose.onNodeWithText("Cerrar aviso").performClick()
            scroll("Ver movimientos"); compose.onNodeWithText("Ver movimientos").performClick()
            scroll("CRÉDITO · HIST-001")
            compose.onAllNodesWithText("Ver productos").onLast().performClick()
            compose.onNodeWithText("Arroz histórico\n2 × $10.00 MXN = $20.00 MXN").assertExists()
            val checkout = requests.single { it.requestUrl!!.encodedPath == "/api/v1/pos/checkout" }
            val input = app.json.decodeFromString<CheckoutRequest>(checkout.body.readUtf8())
            assertEquals("CREDIT", input.settlementType); assertEquals(7L, input.customerId); assertEquals(zero.compareTo(input.receivedAmount), 0)
            assertNotNull(checkout.getHeader("Idempotency-Key")); assertEquals("Bearer fixture-access", checkout.getHeader("Authorization"))
            val payment = requests.single { it.requestUrl!!.encodedPath.endsWith("/payments") }
            assertNotNull(payment.getHeader("Idempotency-Key")); assertEquals(BigDecimal("32.30"), app.json.decodeFromString<CreditPaymentRequest>(payment.body.readUtf8()).amount)
            val creation = requests.single { it.requestUrl!!.encodedPath == "/api/v1/customers" && it.method == "POST" }
            val createdBody = app.json.decodeFromString<CustomerRequest>(creation.body.readUtf8()); assertNull(createdBody.phone); assertNull(createdBody.lastName)
        } finally { scenario?.close(); runBlocking { app.vault.clear(); app.preferences.forgetServer() }; server.shutdown() }
    }
}
