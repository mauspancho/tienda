package com.mauspancho.tiendapos.ui.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mauspancho.tiendapos.ui.components.*

@Composable fun ServerScreen(vm: ServerViewModel) {
    val url by vm.url.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Tienda POS", style = MaterialTheme.typography.headlineLarge)
        Text("Configurar servidor", style = MaterialTheme.typography.titleLarge)
        Text("Ingresa la dirección de tu tienda. Usa HTTPS para un servidor en Internet.")
        OutlinedTextField(url, vm::edit, Modifier.fillMaxWidth(), label = { Text("URL del servidor") }, singleLine = true,
            enabled = !operation.loading, placeholder = { Text("https://tienda.ejemplo.com") })
        Action("PROBAR CONEXIÓN", !connection.loading && !operation.loading, vm::test)
        if (connection.loading) Loading()
        ErrorCard(connection.error, vm::test)
        connection.value?.let {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Servidor encontrado", style = MaterialTheme.typography.titleMedium)
                Text("Servidor ${it.serverVersion} · API ${it.apiVersion}")
            } }
        }
        ErrorCard(operation.error)
        if (operation.loading) Loading()
        Action("GUARDAR Y CONTINUAR", connection.value != null && connection.error == null && !connection.loading && !operation.loading, vm::save)
    }
}
