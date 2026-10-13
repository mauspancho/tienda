package com.mauspancho.tiendapos.ui.dashboard

import com.mauspancho.tiendapos.data.api.ApiClient
import com.mauspancho.tiendapos.data.dto.Dashboard
import com.mauspancho.tiendapos.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow

class DashboardViewModel(private val client: ApiClient) : TaskViewModel(client.json) {
    val state = MutableStateFlow(TaskState<Dashboard>())
    init { refresh() }
    fun refresh() { if (!state.value.loading) task(state) { client.api.dashboard() } }
}
