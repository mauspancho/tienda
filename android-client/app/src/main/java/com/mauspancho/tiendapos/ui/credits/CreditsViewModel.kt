package com.mauspancho.tiendapos.ui.credits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mauspancho.tiendapos.data.api.*
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.domain.repository.CreditRepository
import com.mauspancho.tiendapos.ui.components.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.util.UUID
import com.mauspancho.tiendapos.data.storage.PendingOperationStore

class CreditsViewModel(json: Json, val repository: CreditRepository, private val saved: SavedStateHandle,
    private val durable: PendingOperationStore? = null) : TaskViewModel(json) {
    val available = MutableStateFlow(TaskState<Boolean>())
    val summary = MutableStateFlow(TaskState<CreditSummary>())
    val customers = MutableStateFlow(TaskState<Page<CreditCustomer>>())
    val statement = MutableStateFlow(TaskState<CreditStatement>())
    val query = MutableStateFlow("")
    val filter = MutableStateFlow("DEBT")
    val selected = MutableStateFlow(saved.get<Long>("selectedCustomer"))
    val payment = MutableStateFlow(TaskState<CreditPaymentResult>())
    val attempt = MutableStateFlow(saved.get<String>("creditPayment")?.let { json.decodeFromString<CreditPaymentAttempt>(it) })
    val restoring = MutableStateFlow(durable != null)
    private var listJob: Job? = null
    private var statementJob: Job? = null
    init {
        if (attempt.value != null) selected.value = attempt.value!!.customerId
        refresh()
        if (durable != null) viewModelScope.launch {
            try {
                val raw = durable.read() ?: saved.get<String>("creditPayment")
                if (raw != null) {
                    val restored = json.decodeFromString<CreditPaymentAttempt>(raw)
                    durable.write(raw); attempt.value = restored; saved["creditPayment"] = raw
                    selected.value = restored.customerId; saved["selectedCustomer"] = restored.customerId
                    if (available.value.value == true) open(restored.customerId)
                }
                restoring.value = false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { operation.value = TaskState(error = Failure("No se pudo recuperar el abono pendiente. No se enviarán nuevos abonos.")) }
        }
    }
    fun refresh() {
        if (available.value.loading) return
        task(available) {
            repository.available().also { supported -> if (supported) {
                task(summary) { repository.summary() }; load(0); selected.value?.let(::open)
            } }
        }
    }
    fun search(value: String) { query.value = value; load(0, true) }
    fun filter(value: String) { filter.value = value; load(0) }
    fun load(page: Int, debounce: Boolean = false) {
        listJob?.cancel()
        val q = query.value; val f = filter.value
        listJob = viewModelScope.launch {
            if (debounce) delay(300)
            customers.value = TaskState(loading = true)
            try { customers.value = TaskState(value = repository.customers(q, f, page)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { customers.value = TaskState(error = e.failure(json)) }
        }
    }
    fun open(id: Long) {
        if (attempt.value != null && attempt.value!!.customerId != id) return
        selected.value = id; saved["selectedCustomer"] = id
        statementJob?.cancel()
        statement.value = TaskState()
        statementJob = task(statement) { repository.statement(id) }
    }
    fun back() { if (attempt.value == null && !payment.value.loading && !operation.value.loading) { selected.value = null; saved.remove<Long>("selectedCustomer"); statement.value = TaskState(); payment.value = TaskState() } }
    fun validatePayment(amount: String, method: String, notes: String): CreditPaymentRequest {
        val customer = statement.value.value?.customer ?: throw IllegalArgumentException("Actualiza el estado de cuenta.")
        require(!statement.value.loading && customer.id == selected.value) { "Espera a que cargue el cliente seleccionado." }
        val balance = customer.balance
        val number = amount.toBigDecimalOrNull() ?: throw IllegalArgumentException("Ingresa un importe válido.")
        require(number > BigDecimal.ZERO && number <= balance) { "El abono debe ser mayor a cero y no superar el saldo." }
        require(number.stripTrailingZeros().scale() <= 2) { "Usa como máximo dos decimales." }
        require(method in listOf("CASH", "CARD", "TRANSFER")) { "Selecciona un método de pago." }
        return CreditPaymentRequest(number, method, notes.trim().ifBlank { null })
    }
    fun pay(request: CreditPaymentRequest? = null) {
        if (restoring.value || payment.value.loading || payment.value.value != null) return
        task(payment) {
            val send = attempt.value ?: run {
                require(request != null) { "Revisa el importe del abono." }
                val id = selected.value ?: throw IllegalArgumentException("Selecciona cliente.")
                validatePayment(request.amount.toPlainString(), request.method, request.notes.orEmpty())
                CreditPaymentAttempt(UUID.randomUUID().toString(), id, request).also {
                    attempt.value = it; saved["creditPayment"] = json.encodeToString(it)
                }
            }
            try {
                durable?.write(json.encodeToString(send))
                repository.payment(send).also {
                    durable?.clear(); attempt.value = null; saved.remove<String>("creditPayment")
                    open(send.customerId); task(summary) { repository.summary() }; load(customers.value.value?.page ?: 0)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val failure = e.failure(json)
                if (!failure.uncertain && failure.status in listOf(400, 422)) { durable?.clear(); attempt.value = null; saved.remove<String>("creditPayment") }
                throw e
            }
        }
    }
    fun dismissPayment() { if (attempt.value == null && !payment.value.loading) payment.value = TaskState() }
    fun reverse(payment: Boolean, id: Long, reason: String) {
        if (operation.value.loading || attempt.value != null) return
        task(operation) {
            require(reason.isNotBlank()) { "Indica el motivo." }
            repository.reverse(payment, id, reason.trim())
            selected.value?.let(::open); task(summary) { repository.summary() }; load(0)
        }
    }
}
