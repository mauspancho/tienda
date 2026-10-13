package com.mauspancho.tiendapos.ui.server

import androidx.lifecycle.viewModelScope
import com.mauspancho.tiendapos.data.api.*
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.domain.model.ServerUrl
import com.mauspancho.tiendapos.ui.components.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

class ServerViewModel(json: Json, private val repository: com.mauspancho.tiendapos.domain.repository.ServerRepository,
    private val saveServer: suspend (String, Health) -> Unit) : TaskViewModel(json) {
    val url = MutableStateFlow("")
    val connection = MutableStateFlow(TaskState<Health>())
    private var testedUrl: String? = null
    private var probe: Job? = null
    fun edit(value: String) { probe?.cancel(); url.value = value; testedUrl = null; connection.value = TaskState() }
    fun test() {
        probe?.cancel()
        val entered = url.value
        probe = task(connection) {
            val normalized = ServerUrl.normalize(entered)
            repository.check(normalized).validate().also { if (url.value == entered) testedUrl = normalized }
        }
    }
    fun save() {
        if (operation.value.loading) return
        task(operation) {
            val valid = testedUrl ?: throw IllegalArgumentException("Prueba la conexión antes de guardar.")
            val health = connection.value.value ?: throw IllegalArgumentException("Prueba la conexión antes de guardar.")
            require(valid == ServerUrl.normalize(url.value)) { "La URL cambió; vuelve a probar la conexión." }
            saveServer(valid, health)
        }
    }
}
