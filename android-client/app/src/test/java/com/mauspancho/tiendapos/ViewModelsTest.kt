package com.mauspancho.tiendapos

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.domain.model.*
import com.mauspancho.tiendapos.domain.repository.*
import com.mauspancho.tiendapos.ui.auth.AuthViewModel
import com.mauspancho.tiendapos.ui.pos.PosViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun scannerAcknowledgesOnlySuccessfulCartAdditions() = runTest(dispatcher) {
        var lookupFails = false
        val repository = object : PosRepository {
            override suspend fun state() = PosState(true, null, listOf("CASH"))
            override suspend fun checkout(attempt: CheckoutAttempt): Sale = error("Unused")
        }
        val products = object : ProductRepository {
            override fun pages(query: String, active: Boolean?, brand: String?) = flowOf(PagingData.empty<Product>())
            override suspend fun findBarcode(code: String): Product {
                if (lookupFails) throw SocketTimeoutException()
                return product()
            }
            override suspend fun search(query: String) = emptyList<Product>()
        }
        val vm = PosViewModel(Json, repository, products, SavedStateHandle())
        var acknowledgements = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.scanAdded.collect { acknowledgements++ } }
        advanceUntilIdle()
        vm.scan("known", 0); advanceUntilIdle()
        assertEquals(1, acknowledgements)
        vm.scan("known", 100); advanceUntilIdle() // Duplicate camera frame.
        assertEquals(1, acknowledgements)
        vm.scan("known", 1300); advanceUntilIdle()
        assertEquals(2, acknowledgements)
        assertEquals(0, vm.cart.value.lines.single().quantity.compareTo(java.math.BigDecimal("2")))
        lookupFails = true
        vm.scan("missing", 2600); advanceUntilIdle()
        assertEquals(2, acknowledgements)
        lookupFails = false
        vm.attempt.value = CheckoutAttempt.create(vm.cart.value, "CASH", java.math.BigDecimal("30"))
        vm.scan("known", 3900); advanceUntilIdle()
        assertEquals(2, acknowledgements)
    }
    @Test fun loginPropagatesServerRolesAndHandlesFailures() = runTest(dispatcher) {
        var success: User? = null
        val repo = object : AuthRepository {
            override val session = MutableStateFlow<Session?>(null)
            override suspend fun login(username: String, password: String): User { require(password.isNotEmpty()) { "Ingresa contraseña." }; return com.mauspancho.tiendapos.session().user }
            override suspend fun restore(): User? = null
            override suspend fun logout() = Unit
        }
        val vm = AuthViewModel(Json, repo) { success = it }
        vm.login("fixture", ""); advanceUntilIdle(); assertNotNull(vm.state.value.error); assertNull(success)
        vm.login("fixture", "test-only"); assertTrue(vm.state.value.loading); advanceUntilIdle()
        assertEquals(listOf("CAJERO"), success?.roles); assertFalse(vm.state.value.loading)
    }
    @Test fun timeoutFreezesCheckoutAndRetryReusesSameKey() = runTest(dispatcher) {
        val keys = mutableListOf<String>()
        val repository = object : PosRepository {
            override suspend fun state() = PosState(true, null, listOf("CARD", "TRANSFER", "CASH"))
            override suspend fun checkout(attempt: CheckoutAttempt): Sale { keys.add(attempt.key); throw SocketTimeoutException() }
        }
        val products = object : ProductRepository {
            override fun pages(query: String, active: Boolean?, brand: String?) = flowOf(PagingData.from(listOf(product())))
            override suspend fun findBarcode(code: String) = product()
            override suspend fun search(query: String) = listOf(product())
        }
        val saved = SavedStateHandle()
        val vm = PosViewModel(Json, repository, products, saved); advanceUntilIdle()
        vm.add(product()); vm.checkout(); vm.checkout(); advanceUntilIdle()
        assertEquals(1, keys.size)
        assertTrue(vm.sale.value.error!!.uncertain); assertEquals(1, vm.cart.value.lines.size)
        val request = vm.attempt.value
        assertEquals("CASH", request!!.request.paymentMethod)
        assertEquals(java.math.BigDecimal("12.30"), request.request.receivedAmount)
        vm.add(product(2)); assertEquals(1, vm.cart.value.lines.size)
        vm.checkout(); advanceUntilIdle(); assertEquals(keys[0], keys[1]); assertEquals(request, vm.attempt.value)
        val recreated = PosViewModel(Json, repository, products, saved); advanceUntilIdle()
        assertEquals(request, recreated.attempt.value)
        assertEquals(vm.cart.value.lines.map { it.product.id to it.quantity }, recreated.cart.value.lines.map { it.product.id to it.quantity })
        assertEquals(0, vm.cart.value.total.compareTo(recreated.cart.value.total))
    }
}
