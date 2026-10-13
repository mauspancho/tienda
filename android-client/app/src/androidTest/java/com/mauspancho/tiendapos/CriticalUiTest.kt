package com.mauspancho.tiendapos

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.domain.repository.*
import com.mauspancho.tiendapos.ui.auth.*
import com.mauspancho.tiendapos.ui.server.*
import com.mauspancho.tiendapos.ui.pos.*
import com.mauspancho.tiendapos.domain.model.CheckoutAttempt
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*

class CriticalUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun cartButtonsKeepQuantityFieldAndApplyInSync() {
        val product = Product(1, "CODE1", "1234567890123", "Producto 1",
            purchaseCost = BigDecimal("5.10"), salePrice = BigDecimal("12.30"), currentStock = BigDecimal("25"), minimumStock = BigDecimal("2"),
            unit = "PIEZA", tax = BigDecimal.ZERO, active = true, promoted = false, promocionWhatsapp = false, lowStock = false)
        val repository = object : PosRepository {
            override suspend fun state() = PosState(true, null, listOf("CASH"))
            override suspend fun checkout(attempt: CheckoutAttempt): Sale = error("Unused")
        }
        val products = object : ProductRepository {
            override fun pages(query: String, active: Boolean?, brand: String?) = flowOf(PagingData.empty<Product>())
            override suspend fun findBarcode(code: String) = product
            override suspend fun search(query: String) = emptyList<Product>()
        }
        val vm = PosViewModel(Json, repository, products, SavedStateHandle())
        vm.add(product)
        compose.setContent { MaterialTheme { PosScreen(vm, false, {}, {}, {}) } }
        compose.onNodeWithTag("pos.list").performScrollToNode(hasText("Cantidad"))
        compose.onNodeWithContentDescription("Incrementar cantidad de Producto 1").performClick()
        compose.onNodeWithText("Cantidad").assertTextContains("2")
        compose.onNodeWithText("Aplicar").performClick()
        compose.runOnIdle { assertEquals(0, vm.cart.value.lines.single().quantity.compareTo(BigDecimal("2"))) }
        compose.onNodeWithContentDescription("Disminuir cantidad de Producto 1").performClick()
        compose.onNodeWithText("Cantidad").assertTextContains("1")
        compose.onNodeWithText("Aplicar").performClick()
        compose.runOnIdle { assertEquals(0, vm.cart.value.lines.single().quantity.compareTo(BigDecimal.ONE)) }
        compose.onNodeWithText("Cantidad").performTextReplacement("2.5")
        compose.onNodeWithText("Aplicar").performClick()
        compose.runOnIdle { assertEquals(0, vm.cart.value.lines.single().quantity.compareTo(BigDecimal("2.5"))) }
        compose.onNodeWithContentDescription("Incrementar cantidad de Producto 1").performClick()
        compose.onNodeWithText("Cantidad").assertTextContains("3.5")
    }
    @Test fun serverMustBeTestedAndEditingInvalidatesValidation() {
        var saved: String? = null
        val vm = ServerViewModel(Json, ServerRepository { Health("UP", "Tienda POS", "v1", "0.1.0") }) { url, _ -> saved = url }
        compose.setContent { MaterialTheme { ServerScreen(vm) } }
        compose.onNodeWithText("GUARDAR Y CONTINUAR").assertIsNotEnabled()
        compose.onNodeWithText("URL del servidor").performTextInput("https://tienda.example")
        compose.onNodeWithText("PROBAR CONEXIÓN").performClick()
        compose.waitUntil(5000) { vm.connection.value.value != null }
        compose.onNodeWithText("Servidor encontrado").assertExists()
        compose.onNodeWithText("GUARDAR Y CONTINUAR").assertIsEnabled().performClick()
        compose.waitUntil(5000) { saved != null }; assertEquals("https://tienda.example/", saved)
        compose.onNodeWithText("URL del servidor").performTextReplacement("https://otro.example")
        compose.onNodeWithText("GUARDAR Y CONTINUAR").assertIsNotEnabled()
    }
    @Test fun loginEmitsServerRolesAndClearsPasswordInput() {
        var signedIn: User? = null
        val user = User(1, "fixture", "Prueba", enabled = true, roles = listOf("CAJERO"))
        val repository = object : AuthRepository {
            override val session = MutableStateFlow<Session?>(null)
            override suspend fun login(username: String, password: String): User { require(username == "fixture" && password == "test-only"); return user }
            override suspend fun restore(): User? = null
            override suspend fun logout() = Unit
        }
        val vm = AuthViewModel(Json, repository) { signedIn = it }
        compose.setContent { MaterialTheme { AuthScreen(vm, "https://tienda.example/", null) {} } }
        compose.onNodeWithText("INICIAR SESIÓN").assertIsNotEnabled()
        compose.onNodeWithText("Usuario").performTextInput("fixture")
        compose.onNodeWithText("Contraseña").performTextInput("test-only")
        compose.onNodeWithText("INICIAR SESIÓN").performClick()
        compose.waitUntil(5000) { signedIn != null }
        assertEquals(listOf("CAJERO"), signedIn!!.roles)
        compose.onNodeWithText("Contraseña").assertTextContains("")
    }
}
