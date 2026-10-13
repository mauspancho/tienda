package com.mauspancho.tiendapos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mauspancho.tiendapos.data.api.ApiClient
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.ui.AppViewModel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.net.InetAddress
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList

class SessionRevocationDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun revokedSessionClosesOldClientBeforeAnotherStoreSignsIn() {
        Assume.assumeTrue(BuildConfig.LAN_HOST == "10.0.2.15")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TiendaApplication
        val requests = CopyOnWriteArrayList<RecordedRequest>(); val server = MockWebServer()
        val a = User(1, "store-a", "Tienda A", enabled = true, roles = listOf("CAJERO"))
        val b = User(2, "store-b", "Tienda B", enabled = true, roles = listOf("CAJERO"))
        var revoked = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                val path = request.requestUrl!!.encodedPath
                val token = request.getHeader("Authorization")
                val body = when {
                    path == "/api/v1/auth/login" -> {
                        val input = app.json.decodeFromString<LoginRequest>(request.body.clone().readUtf8())
                        val user = if (input.username == "store-a") a else b
                        app.json.encodeToString(Session("access-${user.id}", "refresh-${user.id}", "Bearer", 900, user))
                    }
                    path == "/api/v1/auth/refresh" || token == null || (revoked && token == "Bearer access-1") ->
                        return MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json").setBody("{\"status\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"Cuenta o token revocado\"}")
                    path == "/api/v1/auth/me" -> app.json.encodeToString(if (token == "Bearer access-2") b else a)
                    path == "/api/v1/dashboard" -> app.json.encodeToString(Dashboard(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, emptyList(), emptyList()))
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start(InetAddress.getByName("0.0.0.0"), 0)
        var scenario: ActivityScenario<MainActivity>? = null
        fun waitText(text: String) { compose.waitUntil(10000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        fun login(username: String) {
            compose.onNodeWithText("Usuario").performTextReplacement(username)
            compose.onNodeWithText("Contraseña").performTextInput("test-only")
            compose.onNodeWithText("INICIAR SESIÓN").performClick()
        }
        try {
            runBlocking { app.vault.clear(); app.preferences.server("http://10.0.2.15:${server.port}/", "0.1.0", "v1") }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitText("INICIAR SESIÓN"); login("store-a"); waitText("Hola, Tienda A")
            lateinit var old: ApiClient
            scenario.onActivity { old = ViewModelProvider(it).get("app", AppViewModel::class.java).client!! }
            val original = old.sessions.state.value!!
            revoked = true
            runBlocking { assertTrue(runCatching { old.api.me() }.isFailure) }
            waitText("INICIAR SESIÓN")
            assertTrue(runCatching { old.sessions.save(original) }.isFailure)
            login("store-b"); waitText("Hola, Tienda B")
            scenario.onActivity {
                val current = ViewModelProvider(it).get("app", AppViewModel::class.java).client!!
                assertNotSame(old, current); assertEquals(2L, current.sessions.state.value!!.user.id)
            }
            runBlocking { assertTrue(runCatching { old.api.me() }.isFailure) }
            val late = requests.last { it.requestUrl!!.encodedPath == "/api/v1/auth/me" }
            assertNull(late.getHeader("Authorization")); assertNull(old.sessions.state.value)
        } finally { scenario?.close(); runBlocking { app.vault.clear(); app.preferences.forgetServer() }; server.shutdown() }
    }
}
