package com.mauspancho.tiendapos.ui.settings

import com.mauspancho.tiendapos.TiendaApplication
import com.mauspancho.tiendapos.data.api.ApiClient
import com.mauspancho.tiendapos.data.dto.Health
import com.mauspancho.tiendapos.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class SettingsViewModel(private val app: TiendaApplication, private val client: ApiClient) : TaskViewModel(client.json) {
    val health = MutableStateFlow(TaskState<Health>())
    val export = MutableStateFlow(TaskState<File>())
    fun test() { if (!health.value.loading) task(health) { client.auth.health().validate() } }
    fun export(query: Map<String, String>) { if (!export.value.loading) task(export) { app.media.financeCsv(client, query) } }
    fun exported() { export.value = TaskState() }
}
