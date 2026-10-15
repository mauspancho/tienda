package com.mauspancho.tiendapos.ui.whatsapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mauspancho.tiendapos.data.dto.PromotionRequest
import com.mauspancho.tiendapos.ui.components.*

@Composable fun PromotionScreen(vm: PromotionViewModel, back: () -> Unit) {
    val preview by vm.preview.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val pending by vm.attempt.collectAsStateWithLifecycle()
    val name by vm.productName.collectAsStateWithLifecycle()
    val restoring by vm.restoring.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    var message by rememberSaveable { mutableStateOf("") }
    var individual by rememberSaveable { mutableStateOf(false) }
    var phone by rememberSaveable { mutableStateOf("") }
    var review by remember { mutableStateOf<PromotionRequest?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(preview.value) { if (message.isBlank()) message = preview.value?.suggestedMessage.orEmpty() }
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("promotion.list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(back) { Text("Volver") }; Text("Promoción por WhatsApp", style = MaterialTheme.typography.headlineSmall); Text(name) }
        if (restoring || preview.loading || result.loading) item { Loading() }
        item { ErrorCard(operation.error); ErrorCard(preview.error, vm::refresh); if (preview.error?.status == 404) Text("Actualiza el servidor para habilitar promociones desde Android."); ErrorCard(result.error) }
        if (!restoring) {
            pending?.let { attempt -> item { Text("Envío pendiente de confirmar para ${attempt.productName}. Se conservarán el mensaje, el destino y la misma clave."); Text(attempt.request.message)
                Text(if (attempt.request.sendToNumber) "Número: ${attempt.request.phone}" else "Grupos seleccionados al preparar el envío") } }
            result.value?.let { value -> item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (value.processing) "Procesando envío" else "Resultado de la promoción")
                Text("Aceptados por el proveedor: ${value.accepted} · fallidos: ${value.failed}")
                value.groups.forEach { delivery -> Text("${delivery.groupName}: ${when (delivery.status) { "ACCEPTED" -> "Aceptado"; "FAILED" -> "Fallido"; else -> "Pendiente" }}${delivery.error?.let { " · $it" }.orEmpty()}") }
                Text("La aceptación por el proveedor no confirma la lectura. No se reenvía automáticamente.")
                if (!value.processing) TextButton({ vm.newPromotion() }) { Text("Preparar otra promoción") }
            } } } }
            if (pending == null && result.value == null) {
                preview.value?.let { data ->
                    item { Row { FilterChip(!individual, { individual = false }, { Text("Grupos") }); Spacer(Modifier.width(8.dp)); FilterChip(individual, { individual = true }, { Text("Número individual") }) } }
                    if (individual) item { OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Número con código de país") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), enabled = !result.loading)
                        data.individualWarning?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                    else item { Text("Grupos: ${data.groupNames.joinToString().ifBlank { "Sin grupos seleccionados" }}"); data.warning?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                    item { OutlinedTextField(message, { message = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Mensaje de la promoción") }, minLines = 4, enabled = !result.loading) }
                    error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                    item { Action("Revisar promoción", !result.loading && (if (individual) data.individualReady else data.ready)) {
                        try { review = vm.validate(message, individual, phone) } catch (e: IllegalArgumentException) { error = e.message }
                    } }
                }
            }
            if (pending != null) item { Action("Consultar o reintentar el mismo envío", !result.loading) { vm.send() } }
        }
    }
    review?.let { request -> AlertDialog({ review = null }, title = { Text("Enviar promoción") },
        text = { Column { Text(name); Text(if (request.sendToNumber) "Destino: ${request.phone}" else "Grupos: ${preview.value?.groupNames?.joinToString().orEmpty()}"); Text(request.message); Text("Se enviará la imagen del producto con este mensaje.") } },
        confirmButton = { TextButton({ review = null; vm.send(request) }) { Text("Confirmar envío") } }, dismissButton = { TextButton({ review = null }) { Text("Cancelar") } }) }
}
