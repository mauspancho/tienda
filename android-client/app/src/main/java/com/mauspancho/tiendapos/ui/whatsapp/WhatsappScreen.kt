package com.mauspancho.tiendapos.ui.whatsapp

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import androidx.lifecycle.repeatOnLifecycle
import com.mauspancho.tiendapos.ui.components.*
import com.mauspancho.tiendapos.ui.credits.creditDate
import kotlinx.coroutines.*

fun whatsappStatus(value: String) = mapOf("NOT_CONFIGURED" to "Sin configurar", "STARTING" to "Iniciando", "WAITING_QR" to "Esperando QR",
    "CONNECTING" to "Conectando", "CONNECTED" to "Conectado", "DISCONNECTED" to "Desconectado", "RECONNECTING" to "Reconectando", "ERROR" to "Error",
    "LISTO" to "Listo", "DESHABILITADO" to "Deshabilitados", "SIN_GRUPOS" to "Sin grupos", "SIN_SESION" to "Sin sesión", "DESCONECTADO" to "Desconectado",
    "PROVEEDOR_NO_DISPONIBLE" to "Proveedor no disponible", "TIENDA_SUSPENDIDA" to "Tienda suspendida", "IDLE" to "Sin actualización en curso",
    "QUEUED" to "En cola", "RUNNING" to "Actualizando", "COMPLETED" to "Completado", "FAILED" to "Fallido")[value] ?: value

@Composable fun WhatsappScreen(vm: WhatsappViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val qr by vm.qr.collectAsStateWithLifecycle()
    val selected by vm.selectedGroups.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var confirm by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm) { vm.refresh() }
    LaunchedEffect(vm, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (isActive) { delay(3000); vm.pollSync()
            if (vm.state.value.value?.connection?.status in setOf("WAITING_QR", "CONNECTING", "STARTING", "RECONNECTING")) vm.refresh()
        }
    } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.hideQr() }
    DisposableEffect(vm) { onDispose { vm.hideQr() } }
    val bitmap by produceState<android.graphics.Bitmap?>(null, qr.value) {
        value = qr.value?.let { bytes -> withContext(Dispatchers.Default) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
        } }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("whatsapp.list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("WhatsApp", style = MaterialTheme.typography.headlineMedium); TextButton(vm::refresh, enabled = !state.loading && !operation.loading) { Text("Actualizar") } }
        if (state.loading || operation.loading) item { Loading() }
        item { ErrorCard(state.error, vm::refresh); if (state.error?.status == 404) Text("El servidor necesita la actualización de WhatsApp para Android."); ErrorCard(operation.error) }
        state.value?.let { data ->
            val busy = state.loading || operation.loading
            val connected = data.connection.status == "CONNECTED"
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(whatsappStatus(data.connection.status), style = MaterialTheme.typography.titleLarge)
                data.connection.phone?.let { Text("Número vinculado: $it") }; data.connection.providerName?.let { Text("Proveedor: $it") }
                data.connection.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Última consulta: ${creditDate(data.connection.lastCheckedAt)}")
            } } }
            if (!data.connection.providerConfigured) item { Text("Un administrador de plataforma debe configurar WPPConnect en el servidor.") }
            item { Action(if (data.connection.status == "NOT_CONFIGURED") "Conectar WhatsApp" else "Reconectar", !busy && data.connection.providerConfigured) { confirm = if (data.connection.status == "NOT_CONFIGURED") "connect" else "reconnect" } }
            if (data.connection.status != "NOT_CONFIGURED") {
                item { TextButton({ vm.action("refresh") }, enabled = !busy) { Text("Actualizar conexión") } }
                if (!connected) item { Action("Mostrar QR", !busy && !qr.loading, vm::showQr) }
                item { TextButton({ confirm = "disassociate" }, enabled = !busy) { Text("Desvincular de la tienda") } }
            }
            if (qr.loading) item { Loading() }
            item { ErrorCard(qr.error, vm::showQr) }
            bitmap?.let { code -> item { Card { Column(Modifier.padding(16.dp)) {
                Image(code.asImageBitmap(), "QR para vincular WhatsApp", Modifier.fillMaxWidth().height(280.dp))
                Text("Escanea desde WhatsApp → Dispositivos vinculados. El QR se elimina de la pantalla al salir.")
                TextButton(vm::hideQr) { Text("Ocultar QR") }
            } } } }
            item { Text("Grupos y anuncios", style = MaterialTheme.typography.titleLarge) }
            sync.value?.let { value -> item { Text("${whatsappStatus(value.status)} · ${value.groupCount} grupos"); value.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; Text("Última sincronización: ${creditDate(value.lastSuccessfulAt)}") } }
            item { ErrorCard(sync.error, vm::refresh); Action("Sincronizar grupos", connected && !busy && !sync.loading && sync.value?.running != true, vm::startSync) }
            if (sync.loading || sync.value?.running == true) item { Loading() }
            if (data.groups.isEmpty()) item { Empty("Sin grupos. Sincroniza después de conectar WhatsApp.") }
            items(data.groups, key = { it.id }) { group -> Row(Modifier.fillMaxWidth().toggleable(group.id in selected,
                enabled = group.available && connected && !busy && !sync.loading && sync.value?.running != true,
                role = Role.Checkbox, onValueChange = { vm.select(group.id, it) })) {
                Checkbox(group.id in selected, null)
                Column(Modifier.weight(1f).padding(top = 12.dp)) { Text(group.name); if (!group.available) Text("No disponible") }
            } }
            item { Action("Guardar grupos para anuncios", connected && !busy && sync.value?.running != true) { confirm = "groups" } }
            item { Text("Anuncios automáticos: ${whatsappStatus(data.announcements.status)}")
                Action(if (data.announcements.enabled) "Deshabilitar anuncios" else "Habilitar anuncios", !busy && connected) { confirm = if (data.announcements.enabled) "disable" else "enable" } }
            item { Text("Las promociones manuales se preparan desde el detalle de un producto. La programación y n8n se ejecutan en el servidor.") }
        }
    }
    confirm?.let { action -> AlertDialog({ confirm = null }, title = { Text("Confirmar cambio de WhatsApp") },
        text = { Text(when (action) {
            "disassociate" -> "Se eliminará la asociación local y se invalidará la selección de grupos. La sesión del proveedor permanece intacta."
            "groups" -> "Guardar los grupos seleccionados para anuncios de esta tienda."
            "enable" -> "Habilitar anuncios automáticos para los grupos confirmados."
            "disable" -> "Deshabilitar los anuncios automáticos conservando los grupos."
            else -> "Iniciar o reconectar la sesión de WhatsApp de esta tienda."
        }) }, confirmButton = { TextButton({ confirm = null; when (action) { "groups" -> vm.saveGroups(); "enable" -> vm.enabled(true); "disable" -> vm.enabled(false); else -> vm.action(action) } }) { Text("Confirmar") } },
        dismissButton = { TextButton({ confirm = null }) { Text("Cancelar") } }) }
}
