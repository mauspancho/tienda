package com.mauspancho.tiendapos.ui.pos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mauspancho.tiendapos.data.api.*
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.domain.model.*
import com.mauspancho.tiendapos.domain.repository.*
import com.mauspancho.tiendapos.ui.components.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import com.mauspancho.tiendapos.data.storage.PendingOperationStore

class PosViewModel(json: Json, private val repository: PosRepository, private val products: ProductRepository,
    private val saved: SavedStateHandle, private val credits: CreditRepository? = null,
    private val durable: PendingOperationStore? = null) : TaskViewModel(json) {
    val cart = MutableStateFlow(Cart())
    val state = MutableStateFlow(TaskState<PosState>())
    val results = MutableStateFlow(TaskState<List<Product>>())
    val sale = MutableStateFlow(TaskState<Sale>())
    val query = MutableStateFlow("")
    val unknownBarcode = MutableStateFlow<String?>(null)
    private val addedByScanner = MutableSharedFlow<Unit>()
    val scanAdded = addedByScanner.asSharedFlow()
    val attempt = MutableStateFlow(saved.get<String>("checkout")?.let { json.decodeFromString<CheckoutAttempt>(it) })
    val creditMode = MutableStateFlow(saved.get<Boolean>("creditMode") ?: (attempt.value?.request?.settlementType == "CREDIT"))
    val creditCustomer = MutableStateFlow(TaskState<CreditStatement>())
    val saleAccount = MutableStateFlow(TaskState<CreditStatement>())
    val restoring = MutableStateFlow(durable != null)
    private var customerJob: Job? = null
    private var search: Job? = null
    private val gate = BarcodeGate()
    private var scanning = false
    init {
        refresh(); restoreCart(); saved.get<Long>("creditCustomer")?.let(::selectCustomer)
        if (durable != null) viewModelScope.launch {
            try {
                val raw = durable.read() ?: saved.get<String>("checkout")
                if (raw != null) {
                    val restored = json.decodeFromString<CheckoutAttempt>(raw)
                    durable.write(raw); attempt.value = restored; saved["checkout"] = raw
                    creditMode.value = restored.request.settlementType == "CREDIT"; saved["creditMode"] = creditMode.value
                }
                restoring.value = false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { operation.value = TaskState(error = Failure("No se pudo recuperar el cobro pendiente. No se enviarán nuevos cobros.")) }
        }
    }
    fun mode(credit: Boolean) {
        if (restoring.value || attempt.value != null || sale.value.loading) return
        creditMode.value = credit; saved["creditMode"] = credit
    }
    fun selectCustomer(id: Long) {
        if (attempt.value != null || sale.value.loading || credits == null) return
        customerJob?.cancel(); saved["creditCustomer"] = id; creditCustomer.value = TaskState()
        customerJob = task(creditCustomer) { credits.statement(id).also { require(it.customer.active) { "El cliente está inactivo." } } }
    }
    fun refreshSaleAccount() {
        val id = sale.value.value?.customer?.id ?: return
        if (credits != null && !saleAccount.value.loading) task(saleAccount) { credits.statement(id) }
    }
    private fun restoreCart() {
        saved.get<String>("cart")?.let { raw ->
            cart.value = Cart(json.decodeFromString<List<CartLine>>(raw))
        }
    }
    private fun persistCart() {
        saved["cart"] = json.encodeToString(cart.value.lines)
    }
    fun refresh() { if (!state.value.loading) task(state) { repository.state() } }
    fun search(value: String) {
        query.value = value; search?.cancel()
        if (value.isBlank()) { results.value = TaskState(); return }
        search = viewModelScope.launch {
            delay(350)
            val request = task(results) { products.search(value) }
            try { request.join() } finally { request.cancel() }
        }
    }
    fun add(product: Product): Boolean {
        if (restoring.value || attempt.value != null || sale.value.loading) return false
        cart.value = cart.value.add(product); persistCart()
        return true
    }
    fun quantity(id: Long, value: BigDecimal) { if (restoring.value || attempt.value != null || sale.value.loading) return; cart.value = cart.value.quantity(id, value); persistCart() }
    fun remove(id: Long) { if (restoring.value || attempt.value != null || sale.value.loading) return; cart.value = cart.value.remove(id); persistCart() }
    fun scan(code: String, now: Long) {
        if (restoring.value || scanning || attempt.value != null || sale.value.loading || !gate.accept(code, now)) return
        scanning = true
        viewModelScope.launch {
            try {
                if (add(products.findBarcode(code))) {
                    unknownBarcode.value = null
                    addedByScanner.emit(Unit)
                }
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { val failure = e.failure(json); if (failure.status == 404) unknownBarcode.value = code else operation.value = TaskState(error = failure) }
            finally { scanning = false }
        }
    }
    fun checkout() {
        if (restoring.value || sale.value.loading || sale.value.value != null) return
        task(sale) {
            val existing = attempt.value
            val credit = existing?.request?.settlementType == "CREDIT" || (existing == null && creditMode.value)
            if (credit) require("CREDIT" in repository.state().settlementTypes) { "Actualiza el backend para registrar créditos de forma segura." }
            val send = existing ?: run {
                require(state.value.value?.cashOpen == true) { "Abre tu caja antes de registrar una venta." }
                if (!credit) require("CASH" in state.value.value!!.paymentMethods) { "El servidor no permite cobros en efectivo." }
                val customer = if (credit) creditCustomer.value.value?.customer else null
                if (credit) require(customer != null && customer.active && !creditCustomer.value.loading) { "Selecciona un cliente activo y espera a que cargue su estado de cuenta." }
                val base = CheckoutAttempt.create(cart.value, "CASH", if (credit) BigDecimal.ZERO else cart.value.total)
                val created = if (credit) base.copy(request = base.request.copy(customerId = customer!!.id, settlementType = "CREDIT")) else base
                created.also { attempt.value = it; saved["checkout"] = json.encodeToString(it) }
            }
            try {
                durable?.write(json.encodeToString(send))
                repository.checkout(send).also {
                    if (credit && (it.settlementType != "CREDIT" || it.pendingBalance == null)) throw kotlinx.serialization.SerializationException("Respuesta de crédito incompatible.")
                    durable?.clear(); cart.value = Cart(); persistCart(); attempt.value = null; saved.remove<String>("checkout")
                    if (credit && credits != null && it.customer != null) task(saleAccount) { credits.statement(it.customer.id) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val fail = e.failure(json)
                if (!fail.uncertain && fail.status in listOf(400, 422)) { durable?.clear(); attempt.value = null; saved.remove<String>("checkout") }
                throw e
            }
        }
    }
    fun newSale() { if (attempt.value == null) { sale.value = TaskState(); saleAccount.value = TaskState(); refresh() } }
}
