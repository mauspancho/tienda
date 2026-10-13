package com.mauspancho.tiendapos.ui.credits

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.ui.components.*

@Composable fun CustomerPicker(vm: CustomerPickerViewModel, admin: Boolean, select: (Customer) -> Unit, close: () -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val editor by vm.editorOpen.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.search("") }
    FullScreenDialog(close) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Seleccionar cliente", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(query, { vm.search(it) }, Modifier.fillMaxWidth(), label = { Text("Nombre o apellido") }, singleLine = true)
            if (admin) Action("Registrar cliente") { vm.edit() }
            if (customers.loading) Loading()
            ErrorCard(customers.error) { vm.search(query) }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                customers.value?.let { data ->
                    if (data.content.isEmpty()) item { Empty("No se encontraron clientes") }
                    items(data.content, key = { it.id }) { customer ->
                        OutlinedButton({ select(customer); close() }, Modifier.fillMaxWidth(), enabled = customer.active && !customer.name.equals("Público General", true)) {
                            Text(customer.fullName + customer.phone?.let { " · $it" }.orEmpty() + if (!customer.active) " · Inactivo" else "")
                        }
                    }
                }
            }
            customers.value?.let { data -> Row {
                TextButton({ vm.search(query, data.page - 1) }, enabled = data.page > 0 && !customers.loading) { Text("Anterior") }
                Text("${data.page + 1} / ${data.totalPages.coerceAtLeast(1)}", Modifier.padding(12.dp))
                TextButton({ vm.search(query, data.page + 1) }, enabled = data.page + 1 < data.totalPages && !customers.loading) { Text("Siguiente") }
            } }
            TextButton(close) { Text("Volver") }
        }
    }
    if (editor) CustomerEditor(vm) { select(it); close() }
}

@Composable fun CustomerEditor(vm: CustomerPickerViewModel, saved: (Customer) -> Unit) {
    val source by vm.source.collectAsStateWithLifecycle()
    val writing by vm.written.collectAsStateWithLifecycle()
    val similar by vm.similar.collectAsStateWithLifecycle()
    val uncertain by vm.uncertainCreate.collectAsStateWithLifecycle()
    val id by vm.editingId.collectAsStateWithLifecycle()
    val current = source.value
    var name by rememberSaveable(id, current?.id) { mutableStateOf(current?.name.orEmpty()) }
    var lastName by rememberSaveable(id, current?.id) { mutableStateOf(current?.lastName.orEmpty()) }
    var phone by rememberSaveable(id, current?.id) { mutableStateOf(current?.phone.orEmpty()) }
    var notes by rememberSaveable(id, current?.id) { mutableStateOf(current?.notes.orEmpty()) }
    var confirm by remember { mutableStateOf(false) }
    val request = CustomerRequest(name.trim(), lastName.trim().ifBlank { null }, phone.trim().ifBlank { null }, notes.trim().ifBlank { null }, current?.active ?: true)
    LaunchedEffect(writing.value) { writing.value?.let { vm.consumeWritten(); saved(it) } }
    FullScreenDialog(vm::closeEditor) {
        LazyColumn(Modifier.testTag("customer.editor.list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(if (id == null) "Registrar cliente" else "Editar cliente", style = MaterialTheme.typography.headlineSmall) }
            if (source.loading) item { Loading() }
            item { ErrorCard(source.error) { vm.edit(id) }; ErrorCard(writing.error) }
            if (!source.loading && (id == null || current != null)) {
                item { OutlinedTextField(name, { name = it; vm.similar(it) }, Modifier.fillMaxWidth(), label = { Text("Nombre *") }, enabled = !writing.loading) }
                item { OutlinedTextField(lastName, { lastName = it }, Modifier.fillMaxWidth(), label = { Text("Apellido (opcional)") }, enabled = !writing.loading) }
                item { OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Teléfono (opcional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), enabled = !writing.loading) }
                item { OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notas (opcional)") }, enabled = !writing.loading) }
                similar.value?.content?.filter { it.id != id }?.takeIf { it.isNotEmpty() }?.let { matches -> item {
                    Text("Ya hay clientes con un nombre parecido: ${matches.joinToString { it.fullName }}. Revisa antes de guardar.")
                } }
                item { ErrorCard(similar.error) }
                if (id == null && uncertain) item { Text("No se pudo confirmar el alta anterior. Consulta el listado para localizar al cliente antes de registrar otro. No se repetirá automáticamente.") }
                if (writing.loading) item { Loading() }
                item { Action("Guardar cliente", name.isNotBlank() && !writing.loading && !(id == null && uncertain)) { confirm = true } }
            }
            item { TextButton(vm::closeEditor, enabled = !writing.loading) { Text("Volver") } }
        }
    }
    if (confirm) AlertDialog({ confirm = false }, title = { Text("Guardar cliente") },
        text = { Text("${request.name} ${request.lastName.orEmpty()}" + if (similar.value?.content?.any { it.id != id } == true) "\nHay nombres parecidos. Confirma que corresponde a otra persona." else "") },
        confirmButton = { TextButton({ confirm = false; vm.save(id, request) }) { Text("Confirmar") } }, dismissButton = { TextButton({ confirm = false }) { Text("Cancelar") } })
}
