package com.mauspancho.tiendapos

import androidx.lifecycle.SavedStateHandle
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.domain.model.CheckoutAttempt
import com.mauspancho.tiendapos.domain.repository.*
import com.mauspancho.tiendapos.ui.credits.*
import com.mauspancho.tiendapos.ui.pos.PosViewModel
import com.mauspancho.tiendapos.navigation.Destination
import androidx.paging.PagingData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.net.SocketTimeoutException
import com.mauspancho.tiendapos.data.storage.PendingOperationStore

fun creditAccount(balance: String = "100.00") = CreditStatement(
    CreditCustomer(7, "Ana", "López", "Ana López", null, true, BigDecimal(balance), BigDecimal("100.00"), BigDecimal("100.00") - BigDecimal(balance)),
    listOf(CreditSale(12, "F-HIST", "2026-10-08T10:00:00", BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal(balance), "COMPLETED",
        listOf(CreditSaleLine("Nombre histórico", BigDecimal("2"), BigDecimal("50.00"), BigDecimal("100.00"))))), emptyList(), emptyList())

open class FakeCreditRepository : CreditRepository {
    var supported = true
    var account = creditAccount()
    val payments = mutableListOf<CreditPaymentAttempt>()
    val customersWritten = mutableListOf<CustomerRequest>()
    var timeout = false
    override suspend fun available() = supported
    override suspend fun summary() = CreditSummary(account.customer.balance, 1, account.customer.totalGranted, account.customer.totalPaid)
    override suspend fun customers(q: String, filter: String, page: Int) = Page(listOf(account.customer), page, 20, 1, 1)
    override suspend fun customerSearch(q: String, page: Int) = Page(listOf(customer(7)), page, 20, 1, 1)
    override suspend fun customer(id: Long) = Customer(id, "Ana", fullName = "Ana", active = true)
    override suspend fun saveCustomer(id: Long?, request: CustomerRequest): Customer {
        customersWritten.add(request); if (timeout) throw SocketTimeoutException()
        return Customer(id ?: 7, request.name, request.lastName, listOfNotNull(request.name, request.lastName).joinToString(" "), request.phone, request.notes, request.active)
    }
    override suspend fun statement(id: Long) = account
    override suspend fun payment(attempt: CreditPaymentAttempt): CreditPaymentResult {
        payments.add(attempt); if (timeout) throw SocketTimeoutException()
        account = creditAccount("0.00")
        return CreditPaymentResult(99, attempt.request.amount, attempt.request.method, BigDecimal("0.00"), "2026-10-08T12:00:00")
    }
    override suspend fun reverse(payment: Boolean, id: Long, reason: String) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class CreditsTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private val products = object : ProductRepository {
        override fun pages(query: String, active: Boolean?, brand: String?) = flowOf(PagingData.from(listOf(product())))
        override suspend fun findBarcode(code: String) = product()
        override suspend fun search(query: String) = listOf(product())
    }
    private class MemoryPendingStore : PendingOperationStore {
        var raw: String? = null
        override suspend fun read() = raw
        override suspend fun write(value: String) { raw = value }
        override suspend fun clear() { raw = null }
    }
    @Test fun paymentRecoversSameKeyEvenWithANewSavedStateAfterRestart() = runTest(dispatcher) {
        val store = MemoryPendingStore(); val repo = FakeCreditRepository().apply { timeout = true }
        val vm = CreditsViewModel(Json, repo, SavedStateHandle(), store); advanceUntilIdle(); vm.open(7); advanceUntilIdle()
        vm.pay(vm.validatePayment("100", "TRANSFER", "")); advanceUntilIdle()
        assertNotNull(store.raw)
        val cold = CreditsViewModel(Json, repo, SavedStateHandle(), store); advanceUntilIdle()
        assertEquals(7L, cold.selected.value); cold.pay(); advanceUntilIdle()
        assertEquals(repo.payments[0], repo.payments[1])
        repo.timeout = false; cold.pay(); advanceUntilIdle(); assertNull(store.raw)
    }
    @Test fun unreadableCheckpointBlocksNewFinancialWrites() = runTest(dispatcher) {
        val store = object : PendingOperationStore {
            override suspend fun read(): String = error("Corrupt record")
            override suspend fun write(value: String) = Unit
            override suspend fun clear() = Unit
        }
        val repo = FakeCreditRepository(); val vm = CreditsViewModel(Json, repo, SavedStateHandle(), store)
        advanceUntilIdle(); vm.open(7); advanceUntilIdle(); vm.pay(CreditPaymentRequest(BigDecimal.ONE, "CASH")); advanceUntilIdle()
        assertTrue(vm.restoring.value); assertNotNull(vm.operation.value.error); assertTrue(repo.payments.isEmpty())
    }
    @Test fun creditCheckoutRecoversItsBodyFromDurableStoreWithoutSavedCart() = runTest(dispatcher) {
        val store = MemoryPendingStore(); val requests = mutableListOf<CheckoutAttempt>()
        val pos = object : PosRepository {
            override suspend fun state() = PosState(true, paymentMethods = listOf("CASH"), settlementTypes = listOf("IMMEDIATE", "CREDIT"))
            override suspend fun checkout(attempt: CheckoutAttempt): Sale { requests.add(attempt); throw SocketTimeoutException() }
        }
        val vm = PosViewModel(Json, pos, products, SavedStateHandle(), FakeCreditRepository(), store)
        advanceUntilIdle(); vm.mode(true); vm.selectCustomer(7); advanceUntilIdle(); vm.add(product()); vm.checkout(); advanceUntilIdle()
        val cold = PosViewModel(Json, pos, products, SavedStateHandle(), FakeCreditRepository(), store)
        advanceUntilIdle(); assertTrue(cold.creditMode.value); assertTrue(cold.cart.value.lines.isEmpty())
        cold.checkout(); advanceUntilIdle(); assertEquals(requests[0], requests[1])
    }
    @Test fun oldServerCannotReceiveCreditAsAnImmediateSale() = runTest(dispatcher) {
        var posts = 0
        val pos = object : PosRepository {
            override suspend fun state() = PosState(true, paymentMethods = listOf("CASH"))
            override suspend fun checkout(attempt: CheckoutAttempt): Sale { posts++; error("Unsafe request") }
        }
        val vm = PosViewModel(Json, pos, products, SavedStateHandle(), FakeCreditRepository())
        advanceUntilIdle(); vm.mode(true); vm.selectCustomer(7); advanceUntilIdle(); vm.add(product()); vm.checkout(); advanceUntilIdle()
        assertEquals(0, posts); assertNotNull(vm.sale.value.error); assertNull(vm.attempt.value); assertEquals(1, vm.cart.value.lines.size)
        assertFalse(Json.encodeToString(CheckoutRequest(paymentMethod = "CASH", receivedAmount = BigDecimal.ONE, items = listOf(SaleLineRequest(1, BigDecimal.ONE)))).contains("settlementType"))
    }
    @Test fun creditSalePreservesCustomerBodyAndKeyOnTimeoutAndRestoration() = runTest(dispatcher) {
        val requests = mutableListOf<CheckoutAttempt>()
        val pos = object : PosRepository {
            override suspend fun state() = PosState(true, paymentMethods = listOf("CASH"), settlementTypes = listOf("IMMEDIATE", "CREDIT"))
            override suspend fun checkout(attempt: CheckoutAttempt): Sale { requests.add(attempt); throw SocketTimeoutException() }
        }
        val saved = SavedStateHandle(); val repo = FakeCreditRepository()
        val vm = PosViewModel(Json, pos, products, saved, repo)
        advanceUntilIdle(); vm.mode(true); vm.selectCustomer(7); advanceUntilIdle(); vm.add(product())
        vm.checkout(); vm.checkout(); advanceUntilIdle()
        assertEquals(1, requests.size); assertEquals(7L, requests[0].request.customerId)
        assertEquals("CREDIT", requests[0].request.settlementType); assertEquals(BigDecimal.ZERO, requests[0].request.receivedAmount)
        assertTrue(vm.sale.value.error!!.uncertain); assertEquals(1, vm.cart.value.lines.size)
        val restored = PosViewModel(Json, pos, products, saved, repo); advanceUntilIdle(); restored.checkout(); advanceUntilIdle()
        assertEquals(requests[0], requests[1]); assertEquals(BigDecimal("100.00"), repo.account.customer.balance)
    }
    @Test fun paymentValidationUsesServerBalanceAndDoesNotModifyItOnFailure() = runTest(dispatcher) {
        val repo = FakeCreditRepository(); val vm = CreditsViewModel(Json, repo, SavedStateHandle())
        advanceUntilIdle(); vm.open(7); advanceUntilIdle()
        for (invalid in listOf("0", "-1", "100.01", "1.001", "abc")) assertTrue(runCatching { vm.validatePayment(invalid, "CASH", "") }.isFailure)
        assertTrue(runCatching { vm.validatePayment("1", "CREDIT", "") }.isFailure)
        repo.account = creditAccount("0.00"); vm.open(7); advanceUntilIdle()
        assertTrue(runCatching { vm.validatePayment("1", "CASH", "") }.isFailure)
        assertTrue(repo.payments.isEmpty())
    }
    @Test fun uncertainPaymentFreezesRequestAndReusesItAfterRestoration() = runTest(dispatcher) {
        val repo = FakeCreditRepository().apply { timeout = true }; val saved = SavedStateHandle()
        val vm = CreditsViewModel(Json, repo, saved); advanceUntilIdle(); vm.open(7); advanceUntilIdle()
        vm.pay(vm.validatePayment("25", "TRANSFER", "Abono")); vm.pay(); advanceUntilIdle()
        assertEquals(1, repo.payments.size); assertTrue(vm.payment.value.error!!.uncertain)
        assertEquals(BigDecimal("100.00"), vm.statement.value.value!!.customer.balance)
        val restored = CreditsViewModel(Json, repo, saved); advanceUntilIdle()
        restored.pay(CreditPaymentRequest(BigDecimal("90"), "CARD")); advanceUntilIdle()
        assertEquals(repo.payments[0], repo.payments[1])
        assertEquals(7L, repo.payments[1].customerId)
    }
    @Test fun successfulPaymentDisplaysAuthoritativeBalanceAndHistoricalPrices() = runTest(dispatcher) {
        val repo = FakeCreditRepository(); val vm = CreditsViewModel(Json, repo, SavedStateHandle())
        advanceUntilIdle(); vm.open(7); advanceUntilIdle()
        vm.pay(vm.validatePayment("100", "CARD", "")); advanceUntilIdle()
        assertEquals(BigDecimal("0.00"), vm.payment.value.value!!.customerBalance)
        assertEquals(BigDecimal("0.00"), vm.statement.value.value!!.customer.balance)
        assertNull(vm.attempt.value); assertEquals("Nombre histórico", vm.statement.value.value!!.credits.single().items.single().product)
        assertEquals(BigDecimal("50.00"), vm.statement.value.value!!.credits.single().items.single().unitPrice)
    }
    @Test fun customerAllowsOptionalFieldsAndDoesNotRepeatUnknownCreation() = runTest(dispatcher) {
        val repo = FakeCreditRepository(); val saved = SavedStateHandle(); val vm = CustomerPickerViewModel(Json, repo, saved)
        vm.save(null, CustomerRequest("Ana")); vm.save(null, CustomerRequest("Ana")); advanceUntilIdle()
        assertEquals(1, repo.customersWritten.size); assertNull(repo.customersWritten[0].phone); assertNull(repo.customersWritten[0].lastName)
        repo.timeout = true; vm.edit(); vm.save(null, CustomerRequest("Otro cliente")); advanceUntilIdle()
        assertTrue(vm.uncertainCreate.value)
        val restored = CustomerPickerViewModel(Json, repo, saved); restored.save(null, CustomerRequest("Otro cliente")); advanceUntilIdle()
        assertEquals(2, repo.customersWritten.size)
        assertTrue(Destination.CREDITS.allowed(listOf("CAJERO"))); assertFalse(Destination.CREDITS.allowed(listOf("UNKNOWN")))
    }
}
