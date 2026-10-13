package com.mauspancho.tiendapos.ui.components

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.mauspancho.tiendapos.data.api.*
import com.mauspancho.tiendapos.data.repository.ModuleRepository
import com.mauspancho.tiendapos.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ModuleViewModel(val repository: ModuleRepository, val path: String, private val saved: SavedStateHandle) : TaskViewModel(repository.json) {
    val filters = MutableStateFlow(mapOf<String, String>())
    val pages = filters.flatMapLatest { repository.pages(path, it) }.cachedIn(viewModelScope)
    val summary = MutableStateFlow(TaskState<JsonElement>())
    val detail = MutableStateFlow(TaskState<JsonObject>())
    val form = MutableStateFlow<FormSpec?>(if (saved.get<String>("pendingPayload") != null) ModuleForms.create(path) else null)
    val formSource = MutableStateFlow<JsonObject?>(null)
    val references = MutableStateFlow(TaskState<com.mauspancho.tiendapos.data.dto.Page<JsonObject>>())
    val completed = MutableStateFlow(0)
    val pending = MutableStateFlow(saved.get<String>("pendingPayload") != null)
    private var referenceJob: Job? = null
    fun refresh() { if (!summary.value.loading) task(summary) { repository.read(path, filters.value) } }
    fun open(value: JsonObject) { task(detail) { repository.detail(path, value) } }
    fun create(barcode: String? = null) { formSource.value = null; form.value = ModuleForms.create(path, barcode); operation.value = TaskState() }
    fun edit(source: JsonObject) { formSource.value = source; form.value = ModuleForms.edit(path, source["id"]?.jsonPrimitive?.content.orEmpty(), source); operation.value = TaskState() }
    fun closeForm() { if (!operation.value.loading && !pending.value) form.value = null }
    fun submit(values: Map<String, String>) {
        if (operation.value.loading) return
        task(operation) {
            val spec = form.value ?: throw IllegalArgumentException("Abre el formulario.")
            val previous = saved.get<String>("pendingPayload")
            val body = if (previous != null) json.parseToJsonElement(previous).jsonObject else ModuleForms.payload(spec, values, formSource.value)
            val key = if (!spec.idempotent) null else saved.get<String>("pendingKey") ?: UUID.randomUUID().toString().also {
                saved["pendingKey"] = it; saved["pendingPayload"] = body.toString(); pending.value = true
            }
            try {
                detail.value = TaskState(value = repository.submit(spec, body, key))
                saved.remove<String>("pendingKey"); saved.remove<String>("pendingPayload"); pending.value = false
                form.value = null; completed.value++
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val failure = e.failure(json)
                if (!failure.uncertain && failure.status in listOf(400, 422)) {
                    saved.remove<String>("pendingKey"); saved.remove<String>("pendingPayload"); pending.value = false
                }
                throw e
            }
        }
    }
    fun references(path: String, query: String, page: Int = 0) {
        referenceJob?.cancel()
        referenceJob = viewModelScope.launch { delay(300); task(references) { repository.references(path, query, page) }.join() }
    }
    fun active(source: JsonObject, enabled: Boolean) {
        if (operation.value.loading) return
        task(operation) { detail.value = TaskState(value = repository.active(path, source.getValue("id").jsonPrimitive.long, enabled)); completed.value++ }
    }
    fun reverse(source: JsonObject) {
        if (operation.value.loading) return
        task(operation) { detail.value = TaskState(value = repository.reverse(source.getValue("id").jsonPrimitive.long)); completed.value++ }
    }
    fun deleteUser(source: JsonObject) {
        if (operation.value.loading) return
        task(operation) { repository.deleteUser(source.getValue("id").jsonPrimitive.long); detail.value = TaskState(); completed.value++ }
    }
}
