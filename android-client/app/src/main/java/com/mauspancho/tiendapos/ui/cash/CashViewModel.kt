package com.mauspancho.tiendapos.ui.cash

import com.mauspancho.tiendapos.data.api.ApiClient
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigDecimal

class CashViewModel(private val client: ApiClient) : TaskViewModel(client.json) {
    val state = MutableStateFlow(TaskState<CurrentCash>())
    init { refresh() }
    fun refresh() { if (!state.value.loading) task(state) { client.api.cash() } }
    fun submit(value: String) {
        if (operation.value.loading) return
        task(operation) {
            val amount = value.toBigDecimalOrNull() ?: throw IllegalArgumentException("Ingresa un importe válido.")
            require(amount >= BigDecimal.ZERO) { "El importe debe ser mayor o igual a cero." }
            val current = state.value.value ?: throw IllegalArgumentException("Actualiza el estado de caja.")
            if (current.open) client.api.closeCash(current.session!!.id, CloseCash(amount)) else client.api.openCash(OpenCash(amount))
            state.value = TaskState(value = client.api.cash())
        }
    }
}
