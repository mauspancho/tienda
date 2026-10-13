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

class CustomerPickerViewModel(json: Json, private val repository: CreditRepository, private val saved: SavedStateHandle) : TaskViewModel(json) {
    val query = MutableStateFlow("")
    val customers = MutableStateFlow(TaskState<Page<Customer>>())
    val similar = MutableStateFlow(TaskState<Page<Customer>>())
    val source = MutableStateFlow(TaskState<Customer>())
    val written = MutableStateFlow(TaskState<Customer>())
    val editorOpen = MutableStateFlow(false)
    val editingId = MutableStateFlow<Long?>(null)
    val uncertainCreate = MutableStateFlow(saved.get<Boolean>("uncertainCreate") == true)
    private var searchJob: Job? = null
    private var similarJob: Job? = null
    private var sourceJob: Job? = null
    fun search(q: String, page: Int = 0) {
        query.value = q; searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300); customers.value = TaskState(loading = true)
            try { customers.value = TaskState(value = repository.customerSearch(q, page)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { customers.value = TaskState(error = e.failure(json)) }
        }
    }
    fun similar(name: String) {
        similarJob?.cancel(); similar.value = TaskState()
        if (name.trim().length < 2) return
        similarJob = viewModelScope.launch {
            delay(300); similar.value = TaskState(loading = true)
            try { similar.value = TaskState(value = repository.customerSearch(name.trim(), 0)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { similar.value = TaskState(error = e.failure(json)) }
        }
    }
    fun edit(id: Long? = null) {
        if (written.value.loading) return
        sourceJob?.cancel()
        editingId.value = id; source.value = TaskState(); written.value = TaskState(); similar.value = TaskState(); editorOpen.value = true
        if (id != null) sourceJob = task(source) { repository.customer(id) }
    }
    fun save(id: Long?, request: CustomerRequest) {
        if (written.value.loading || (id == null && uncertainCreate.value)) return
        task(written) {
            require(request.name.isNotBlank()) { "El nombre es obligatorio." }
            require(repository.available()) { "El servidor necesita la actualización de Créditos." }
            try { repository.saveCustomer(id, request) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (id == null && e.failure(json).uncertain) { uncertainCreate.value = true; saved["uncertainCreate"] = true }
                throw e
            }
        }
    }
    fun closeEditor() { if (!written.value.loading) editorOpen.value = false }
    fun consumeWritten() { written.value = TaskState(); editorOpen.value = false; search(query.value) }
}
