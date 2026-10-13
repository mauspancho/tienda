package com.mauspancho.tiendapos.ui.sales

import com.mauspancho.tiendapos.data.api.ApiClient
import com.mauspancho.tiendapos.data.dto.Sale
import com.mauspancho.tiendapos.data.storage.MediaStorage
import com.mauspancho.tiendapos.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class TicketViewModel(private val client: ApiClient, private val media: MediaStorage, val folio: String) : TaskViewModel(client.json) {
    val state = MutableStateFlow(TaskState<Sale>())
    val file = MutableStateFlow(TaskState<File>())
    init { refresh() }
    fun refresh() { if (!state.value.loading) task(state) { client.api.ticket(folio) } }
    fun share() { if (!file.value.loading) task(file) { media.ticket(state.value.value ?: throw IllegalArgumentException("Carga el ticket antes de compartir.")) } }
    fun shared() { file.value = TaskState() }
}
