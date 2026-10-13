package com.mauspancho.tiendapos.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.mauspancho.tiendapos.domain.model.*
import kotlinx.serialization.json.*

@Composable fun ModuleScreen(vm: ModuleViewModel, paged: Boolean, admin: Boolean, ticket: ((String) -> Unit)? = null,
    day: ((String) -> Unit)? = null) {
    val pages = if (paged) vm.pages.collectAsLazyPagingItems() else null
    val summary by vm.summary.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val completed by vm.completed.collectAsStateWithLifecycle()
    var detailVisible by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf(if (vm.path == "reports") "TODAY" else "LAST_30_DAYS") }
    var confirm by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm.path) { if (!paged) vm.refresh() }
    LaunchedEffect(completed) { if (completed > 0) { if (paged) pages?.refresh() else vm.refresh() } }
    Column(Modifier.fillMaxSize()) {
        if (vm.path == "dashboard/profit") {
            OutlinedTextField(from, { from = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), label = { Text("Fecha AAAA-MM-DD (opcional)") }, singleLine = true)
            Row(Modifier.padding(horizontal = 16.dp)) { listOf("day" to "Día", "week" to "Semana", "month" to "Mes").forEach { (value, label) ->
                TextButton({ if (from.isBlank() || runCatching { java.time.LocalDate.parse(from) }.isSuccess) {
                    vm.filters.value = buildMap { put("period", value); if (from.isNotBlank()) put("date", from) }; vm.refresh()
                } else vm.operation.value = TaskState(error = com.mauspancho.tiendapos.data.api.Failure("Usa una fecha válida AAAA-MM-DD.")) }) { Text(label) }
            } }
        }
        if (admin && ModuleForms.create(vm.path) != null) TextButton({
            if (vm.path == "settings") (summary.value as? JsonObject)?.let(vm::edit) else vm.create()
        }, enabled = vm.path != "settings" || summary.value is JsonObject) { Text(ModuleForms.create(vm.path)!!.title) }
        if (vm.path == "purchases") {
            var funding by rememberSaveable { mutableStateOf("") }
            var showFunding by remember { mutableStateOf(false) }
            TextButton({ showFunding = true }) { Text("Origen: ${if (funding.isBlank()) "Todos" else optionLabel(funding)}") }
            if (showFunding) AlertDialog({ showFunding = false }, title = { Text("Origen del dinero") }, text = {
                Column { listOf("", "BUSINESS_CASH", "OWNER_CAPITAL", "SUPPLIER_CREDIT", "OTHER", "UNKNOWN").forEach { value ->
                    TextButton({ funding = value; vm.filters.value = if (value.isBlank()) vm.filters.value - "fundingSource" else vm.filters.value + ("fundingSource" to value); showFunding = false }) { Text(if (value.isBlank()) "Todos" else optionLabel(value)) }
                } }
            }, confirmButton = { TextButton({ showFunding = false }) { Text("Volver") } })
        }
        if (vm.path in listOf("categories", "suppliers", "inventory/stock")) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(16.dp), label = { Text("Buscar") }, singleLine = true)
            LaunchedEffect(query) { kotlinx.coroutines.delay(350); vm.filters.value = mapOf("q" to query) }
        }
        if (vm.path in listOf("purchases", "finances", "finances/capital", "finances/daily")) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from, { from = it }, Modifier.weight(1f), label = { Text("Desde AAAA-MM-DD") }, singleLine = true)
                OutlinedTextField(to, { to = it }, Modifier.weight(1f), label = { Text("Hasta AAAA-MM-DD") }, singleLine = true)
            }
            TextButton({
                if ((from.isBlank() || runCatching { java.time.LocalDate.parse(from) }.isSuccess) && (to.isBlank() || runCatching { java.time.LocalDate.parse(to) }.isSuccess)) {
                    vm.filters.value = (vm.filters.value - "from" - "to") + buildMap { if (from.isNotBlank()) put("from", from); if (to.isNotBlank()) put("to", to); if (vm.path == "finances") put("period", "CUSTOM") }
                    if (!paged) vm.refresh()
                } else vm.operation.value = TaskState(error = com.mauspancho.tiendapos.data.api.Failure("Usa fechas válidas AAAA-MM-DD."))
            }) { Text("Aplicar fechas") }
        }
        if (vm.path == "reports") {
            Row(Modifier.padding(horizontal = 16.dp)) { listOf("TODAY" to "Hoy", "WEEK" to "Semana", "MONTH" to "Mes").forEach { (key, label) ->
                FilterChip(period == key, { period = key; vm.filters.value = mapOf("period" to key); vm.refresh() }, label = { Text(label) }, modifier = Modifier.padding(end = 8.dp))
            } }
        }
        ErrorCard(operation.error)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pages != null) {
                item { PagingStatus(pages.loadState.refresh, vm.repository.json, pages::retry) }
                if (pages.itemCount == 0 && pages.loadState.refresh is androidx.paging.LoadState.NotLoading) item { Empty() }
                items(pages.itemCount, key = { index -> pages.peek(index)?.get("id")?.jsonPrimitive?.content ?: "row$index" }) { index ->
                    pages[index]?.let { entry -> Card(onClick = { detailVisible = true; vm.open(entry) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(entry.title(), style = MaterialTheme.typography.titleMedium)
                            listOf("saleDate", "purchaseDate", "expenseDate", "createdAt", "total", "amount", "type", "status", "currentStock", "username")
                                .forEach { key -> (entry[key] as? JsonPrimitive)?.contentOrNull?.let { Text("${labels[key]}: ${optionLabel(it)}") } }
                        }
                    } }
                }
                item { PagingStatus(pages.loadState.append, vm.repository.json, pages::retry) }
                item { TextButton(pages::refresh) { Text("Actualizar") } }
            } else {
                if (summary.loading) item { Loading() }
                item { ErrorCard(summary.error, vm::refresh) }
                summary.value?.let { entry ->
                    if (vm.path == "finances/daily" && entry is JsonArray && day != null) items(entry.toList()) { value ->
                        Card(onClick = { day(value.jsonObject.getValue("date").jsonPrimitive.content) }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { NativeDetails(value) } }
                    } else item { NativeDetails(entry) }
                }
                item { TextButton(vm::refresh) { Text("Actualizar") } }
            }
        }
    }
    if (detailVisible) FullScreenDialog({ detailVisible = false }) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Row { TextButton({ detailVisible = false }) { Text("Volver") }; Text("Detalle", style = MaterialTheme.typography.titleLarge) } }
            if (detail.loading) item { Loading() }
            item { ErrorCard(detail.error) }
            detail.value?.let { entry ->
                item { NativeDetails(entry) }
                if (vm.path == "sales" && ticket != null) item { Action("Ver ticket") { ticket(entry.getValue("folio").jsonPrimitive.content) } }
                if (admin && ModuleForms.edit(vm.path, entry["id"]?.jsonPrimitive?.content.orEmpty(), entry) != null) item { Action("Editar") { vm.edit(entry) } }
                if (admin && vm.path in listOf("categories", "suppliers", "users")) item { Action(if ((entry[if (vm.path == "users") "enabled" else "active"] as? JsonPrimitive)?.booleanOrNull == true) "Desactivar" else "Activar", !operation.loading) { confirm = "active" } }
                if (admin && vm.path == "inventory/movements" && (entry["reversible"] as? JsonPrimitive)?.booleanOrNull == true) item { Action("Retirar movimiento", !operation.loading) { confirm = "reverse" } }
                if (admin && vm.path == "users") item { Action("Eliminar o desactivar usuario", !operation.loading) { confirm = "delete" } }
            }
            item { ErrorCard(operation.error) }
        }
    }
    confirm?.let { action -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Confirmar acción") }, text = { Text("¿Deseas realizar esta operación? El servidor validará si está permitida.") },
        confirmButton = { TextButton({ detail.value?.let { value -> when (action) {
            "active" -> vm.active(value, (value[if (vm.path == "users") "enabled" else "active"] as? JsonPrimitive)?.booleanOrNull != true)
            "reverse" -> vm.reverse(value)
            "delete" -> { vm.deleteUser(value); detailVisible = false }
        } }; confirm = null }) { Text("Confirmar") } }, dismissButton = { TextButton({ confirm = null }) { Text("Cancelar") } }) }
    form?.let { FormDialog(vm, it) }
}
@Composable fun FullScreenDialog(close: () -> Unit, content: @Composable () -> Unit) {
    Dialog(close, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) { content() }
    }
}
@Composable fun FormDialog(vm: ModuleViewModel, spec: FormSpec) {
    val operation by vm.operation.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val saver = remember { Saver<Map<String, String>, HashMap<String, String>>(save = { HashMap(it) }, restore = { it.toMap() }) }
    var values by rememberSaveable(spec.path, stateSaver = saver) { mutableStateOf(spec.fields.filterNot { it.kind == InputKind.PASSWORD }.associate { it.key to it.initial }) }
    var secret by remember(spec.path) { mutableStateOf("") }
    var reference by remember { mutableStateOf<FormField?>(null) }
    var referenceNames by remember { mutableStateOf(mapOf<String, String>()) }
    fun set(key: String, value: String) { values = values + (key to value) }
    FullScreenDialog(vm::closeForm) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(spec.title, style = MaterialTheme.typography.headlineSmall) }
            if (spec.path.startsWith("users/") && spec.edit) item { Text("Vuelve a ingresar nombre y apellidos completos.") }
            if (pending) item { Text("Hay una operación pendiente de confirmar. El reintento conserva los mismos datos.") }
            items(spec.fields, key = { it.key }) { field ->
                val value = if (field.kind == InputKind.PASSWORD) secret else values[field.key] ?: field.initial
                when (field.kind) {
                    InputKind.BOOLEAN -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(field.label, Modifier.weight(1f).padding(top = 12.dp)); Switch(value == "true", { set(field.key, "$it") }, enabled = !operation.loading && !pending)
                    }
                    InputKind.CHOICE -> Column { Text(field.label); field.options.forEach { option -> Row {
                        RadioButton(value == option, { set(field.key, option) }, enabled = !operation.loading && !pending)
                        TextButton({ set(field.key, option) }, enabled = !operation.loading && !pending) { Text(optionLabel(option)) }
                    } } }
                    InputKind.ROLES -> Column { Text(field.label); field.options.forEach { option -> Row {
                        Checkbox(option in value.split(','), { selected -> set(field.key, (if (selected) value.split(',') + option else value.split(',') - option).filter { it.isNotBlank() }.distinct().joinToString(",")) }, enabled = !operation.loading && !pending)
                        Text(optionLabel(option), Modifier.padding(top = 12.dp))
                    } } }
                    InputKind.REFERENCE -> OutlinedButton({ reference = field; vm.references(field.reference!!, "") }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !operation.loading && !pending) {
                        Text("${field.label}: ${referenceNames[field.key] ?: if (value.isBlank()) "Seleccionar" else "selección actual"}")
                    }
                    else -> OutlinedTextField(value, { if (field.kind == InputKind.PASSWORD) secret = it else set(field.key, it) }, Modifier.fillMaxWidth(),
                        label = { Text(field.label + if (field.required) " *" else "") }, enabled = !operation.loading && !pending,
                        singleLine = field.key !in listOf("notes", "description", "address"),
                        visualTransformation = if (field.kind == InputKind.PASSWORD) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(keyboardType = when (field.kind) { InputKind.DECIMAL -> KeyboardType.Decimal; InputKind.ID -> KeyboardType.Number; InputKind.PASSWORD -> KeyboardType.Password; else -> KeyboardType.Text }))
                }
            }
            item { ErrorCard(operation.error) }
            if (operation.loading) item { Loading() }
            item { Action(if (pending) "Reintentar la misma operación" else "Guardar", !operation.loading) { vm.submit(values + ("password" to secret)); secret = "" } }
            if (!pending) item { TextButton(vm::closeForm, enabled = !operation.loading) { Text("Cancelar") } }
        }
    }
    reference?.let { field ->
        val state by vm.references.collectAsStateWithLifecycle()
        var q by rememberSaveable(field.key) { mutableStateOf("") }
        FullScreenDialog({ reference = null }) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(field.label, style = MaterialTheme.typography.headlineSmall)
                if (field.reference !in listOf("users", "expenses/categories")) OutlinedTextField(q, { q = it; vm.references(field.reference!!, it) }, Modifier.fillMaxWidth(), label = { Text("Buscar") }, singleLine = true)
                if (state.loading) Loading()
                ErrorCard(state.error) { vm.references(field.reference!!, q) }
                LazyColumn(Modifier.weight(1f)) {
                    state.value?.content?.let { rows -> if (rows.isEmpty()) item { Empty() }; items(rows) { entry -> TextButton({
                        set(field.key, entry.getValue("id").jsonPrimitive.content)
                        if (spec.path == "purchases" && field.key == "productId") {
                            set("unitCost", entry["purchaseCost"]?.jsonPrimitive?.contentOrNull.orEmpty())
                        }
                        referenceNames = referenceNames + (field.key to entry.title()); reference = null
                    }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(entry.title() + listOfNotNull(entry["brand"]?.jsonPrimitive?.contentOrNull, entry["code"]?.jsonPrimitive?.contentOrNull, entry["presentation"]?.jsonPrimitive?.contentOrNull).joinToString(" · ", prefix = " ")) } } }
                }
                state.value?.let { data -> Row {
                    TextButton({ vm.references(field.reference!!, q, data.page - 1) }, enabled = data.page > 0 && !state.loading) { Text("Anterior") }
                    TextButton({ vm.references(field.reference!!, q, data.page + 1) }, enabled = data.page + 1 < data.totalPages && !state.loading) { Text("Siguiente") }
                } }
                if (!field.required) TextButton({ set(field.key, ""); referenceNames = referenceNames - field.key; reference = null }) { Text("Sin selección") }
                TextButton({ reference = null }) { Text("Volver") }
            }
        }
    }
}
