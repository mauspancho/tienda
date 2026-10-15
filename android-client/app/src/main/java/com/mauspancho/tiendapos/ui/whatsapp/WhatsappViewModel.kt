package com.mauspancho.tiendapos.ui.whatsapp

import com.mauspancho.tiendapos.data.api.failure
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.domain.repository.WhatsappRepository
import com.mauspancho.tiendapos.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

class WhatsappViewModel(json: Json, private val repository: WhatsappRepository) : TaskViewModel(json) {
    val state = MutableStateFlow(TaskState<WhatsappState>())
    val qr = MutableStateFlow(TaskState<ByteArray>())
    val sync = MutableStateFlow(TaskState<WhatsappSync>())
    val selectedGroups = MutableStateFlow<Set<Long>>(emptySet())
    private var qrJob: Job? = null
    private fun accept(value: WhatsappState) {
        selectedGroups.value = value.groups.filter { it.selectedForAnnouncements }.map { it.id }.toSet()
        sync.value = TaskState(value = value.sync)
        if (value.connection.status == "CONNECTED" || value.connection.status == "NOT_CONFIGURED") hideQr()
    }
    fun refresh() { if (!state.value.loading && !operation.value.loading) task(state) { repository.state().also(::accept) } }
    fun action(action: String) {
        if (operation.value.loading || state.value.loading) return
        task(operation) { hideQr(); repository.action(action).also { accept(it); state.value = TaskState(value = it) }; Unit }
    }
    fun showQr() { if (!qr.value.loading) qrJob = task(qr) { repository.qr() } }
    fun hideQr() { qrJob?.cancel(); qr.value = TaskState() }
    fun select(id: Long, selected: Boolean) {
        if (operation.value.loading || sync.value.loading || sync.value.value?.running == true) return
        selectedGroups.value = if (selected) selectedGroups.value + id else selectedGroups.value - id
    }
    fun saveGroups() {
        if (operation.value.loading || state.value.loading) return
        val ids = selectedGroups.value.sorted()
        task(operation) { repository.groups(ids).also { accept(it); state.value = TaskState(value = it) }; Unit }
    }
    fun enabled(value: Boolean) {
        if (operation.value.loading || state.value.loading) return
        task(operation) { repository.announcements(value).also { accept(it); state.value = TaskState(value = it) }; Unit }
    }
    fun startSync() {
        if (sync.value.loading || sync.value.value?.running == true || operation.value.loading) return
        task(sync) { repository.startSync() }
    }
    suspend fun pollSync() {
        if (sync.value.loading || sync.value.error != null || sync.value.value?.running != true) return
        try {
            val value = repository.sync(); sync.value = TaskState(value = value)
            if (!value.running) refresh()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { sync.value = sync.value.copy(error = e.failure(json)) }
    }
}
