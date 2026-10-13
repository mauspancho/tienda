package com.mauspancho.tiendapos.ui.credits

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mauspancho.tiendapos.data.api.Failure
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.domain.model.optionLabel
import com.mauspancho.tiendapos.ui.components.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun BigDecimal.mxn() = "$${money()} MXN"
fun creditDate(value: String?) = value?.let { runCatching { LocalDateTime.parse(it).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.forLanguageTag("es-MX"))) }.getOrDefault(it) } ?: "Sin movimientos"

@Composable fun CreditsScreen(vm: CreditsViewModel, picker: CustomerPickerViewModel, admin: Boolean, newCredit: (Long?) -> Unit) {
    val supported by vm.available.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val statement by vm.statement.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val editor by picker.editorOpen.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val payment by vm.payment.collectAsStateWithLifecycle()
    val pending by vm.attempt.collectAsStateWithLifecycle()
    val restoring by vm.restoring.collectAsStateWithLifecycle()
    var movements by rememberSaveable(selected) { mutableStateOf(false) }
    var paying by rememberSaveable(selected) { mutableStateOf(false) }
    var reversal by remember { mutableStateOf<Pair<Boolean, Long>?>(null) }
    if (restoring) { Column(Modifier.padding(16.dp)) { Loading(); ErrorCard(operation.error) }; return }
    LaunchedEffect(Unit) { vm.refresh() }
    LazyColumn(Modifier.fillMaxSize().testTag("credits.list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Créditos", style = MaterialTheme.typography.headlineMedium); TextButton(vm::refresh, enabled = !supported.loading && !payment.loading && !operation.loading) { Text("Actualizar") }; ErrorCard(supported.error, vm::refresh) }
        if (supported.loading) item { Loading() }
        if (supported.value == false) item { Text("El servidor todavía no tiene Créditos. Actualiza el backend para habilitar clientes, fiados y abonos.") }
        if (supported.value == true) {
            if (selected == null) {
                if (summary.loading) item { Loading() }
                item { ErrorCard(summary.error, vm::refresh) }
                summary.value?.let { totals -> item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text("Pendiente de cobro ${totals.pending.mxn()}", style = MaterialTheme.typography.titleLarge)
                    Text("${totals.customersWithDebt} clientes con adeudo")
                } } } }
                item { OutlinedTextField(query, vm::search, Modifier.fillMaxWidth(), label = { Text("Buscar nombre o apellido") }, singleLine = true) }
                item { Row { listOf("DEBT" to "Pendientes", "WITHOUT_DEBT" to "Liquidados", "ALL" to "Todos").forEach { (key, label) ->
                    FilterChip(filter == key, { vm.filter(key) }, label = { Text(label) }, modifier = Modifier.padding(end = 4.dp))
                } } }
                if (admin) item { Action("Registrar cliente") { picker.edit() } }
                item { Action("Crear crédito") { newCredit(null) } }
                if (customers.loading) item { Loading() }
                item { ErrorCard(customers.error) { vm.load(0) } }
                customers.value?.let { page ->
                    if (page.content.isEmpty()) item { Empty("No hay clientes en este filtro") }
                    items(page.content, key = { it.id }) { customer -> Card(onClick = { vm.open(customer.id) }, modifier = Modifier.fillMaxWidth()) {
                        ListItem(headlineContent = { Text(customer.fullName) }, supportingContent = { Text(if (customer.balance.signum() > 0) "Pendiente" else "Liquidado") }, trailingContent = { Text(customer.balance.mxn()) })
                    } }
                    item { Row { TextButton({ vm.load(page.page - 1) }, enabled = page.page > 0 && !customers.loading) { Text("Anterior") }; Text("${page.page + 1} / ${page.totalPages.coerceAtLeast(1)}", Modifier.padding(12.dp)); TextButton({ vm.load(page.page + 1) }, enabled = page.page + 1 < page.totalPages && !customers.loading) { Text("Siguiente") } } }
                }
            } else {
                item { TextButton(vm::back, enabled = pending == null && !payment.loading && !operation.loading) { Text("Volver a clientes") }; ErrorCard(statement.error) { vm.open(selected!!) }; ErrorCard(operation.error) }
                if (statement.loading) item { Loading() }
                payment.value?.let { result -> item { Card { Column(Modifier.padding(16.dp)) {
                    Text("Abono registrado · AB-${result.id}"); Text("${result.amount.mxn()} · saldo confirmado ${result.customerBalance.mxn()}")
                    Text(if (result.customerBalance.signum() == 0) "Liquidado" else "Pendiente")
                    TextButton({ vm.dismissPayment(); paying = false }) { Text("Cerrar aviso") }
                } } } }
                statement.value?.let { account ->
                    val c = account.customer
                    item { Text(c.fullName, style = MaterialTheme.typography.headlineSmall); c.phone?.let { Text(it) }; Text("Saldo ${c.balance.mxn()}"); Text(if (c.balance.signum() > 0) "Pendiente" else "Liquidado"); Text("Última actividad: ${creditDate(c.lastActivity)}") }
                    item { Row { FilterChip(!movements, { movements = false }, { Text("Resumen") }); Spacer(Modifier.width(8.dp)); FilterChip(movements, { movements = true }, { Text("Movimientos") }) } }
                    if (!movements) {
                        item { Text("Total prestado ${c.totalGranted.mxn()}"); Text("Total abonado ${c.totalPaid.mxn()}"); Text("${account.credits.count { it.balance.signum() > 0 }} ventas pendientes")
                            if (account.charges.isNotEmpty()) Text("${account.charges.count { it.balance.signum() > 0 }} cargos manuales pendientes") }
                        items(account.charges, key = { "charge.${it.id}" }) { charge -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text("Cargo manual · ${charge.concept}", style = MaterialTheme.typography.titleMedium)
                            Text(creditDate(charge.date)); Text("Importe ${charge.amount.mxn()} · abonado ${charge.applied.mxn()}"); Text("Saldo pendiente ${charge.balance.mxn()}")
                            Text(when { charge.status == "REVERSED" -> "Revertido"; charge.balance.signum() == 0 -> "Liquidado"; else -> "Pendiente" })
                        } } }
                    }
                    if (admin) item { TextButton({ picker.edit(c.id) }, enabled = pending == null && !payment.loading && !operation.loading) { Text("Editar cliente") } }
                    item { Action("Nuevo crédito", c.active && pending == null && !payment.loading && !operation.loading) { newCredit(c.id) } }
                    item { Action("Registrar abono", c.balance.signum() > 0 && pending == null && !payment.loading && !operation.loading) { vm.dismissPayment(); paying = true } }
                    item { TextButton({ movements = true }) { Text("Ver movimientos") } }
                    if (movements) {
                        if (account.movements.isEmpty()) item { Empty("Sin movimientos") }
                        itemsIndexed(account.movements) { index, movement ->
                            var expanded by rememberSaveable(c.id, index) { mutableStateOf(false) }
                            val sale = if (movement.type == "CRÉDITO") account.credits.find { it.id == movement.entityId } else null
                            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${movement.type} · ${movement.reference}", style = MaterialTheme.typography.titleMedium)
                                Text(creditDate(movement.date)); Text("Cargo ${movement.charge.mxn()} · abono ${movement.payment.mxn()}"); Text("Saldo posterior ${movement.resultingBalance.mxn()}")
                                movement.detail?.takeIf { it.isNotBlank() }?.let { Text(it) }
                                if (sale != null) {
                                    TextButton({ expanded = !expanded }) { Text(if (expanded) "Ocultar productos" else "Ver productos") }
                                    if (expanded) sale.items.forEach { line -> Text("${line.product}\n${line.quantity.toPlainString()} × ${line.unitPrice.mxn()} = ${line.subtotal.mxn()}") }
                                    if (admin && sale.status == "COMPLETED") TextButton({ vm.operation.value = TaskState(); reversal = false to sale.id }, enabled = !operation.loading && pending == null && !payment.loading) { Text("Cancelar crédito") }
                                }
                                if (admin && movement.type == "ABONO" && !movement.reversed) TextButton({ vm.operation.value = TaskState(); reversal = true to movement.entityId }, enabled = !operation.loading && pending == null && !payment.loading) { Text("Revertir abono") }
                            } }
                        }
                    }
                }
            }
        }
    }
    if (editor && admin) CustomerEditor(picker) { customer -> vm.open(customer.id); vm.refresh() }
    if (paying || pending != null) PaymentDialog(vm) { paying = false }
    reversal?.let { action ->
        var reason by rememberSaveable(action) { mutableStateOf("") }
        AlertDialog({ if (!operation.loading) reversal = null }, title = { Text(if (action.first) "Revertir abono" else "Cancelar crédito") },
            text = { Column { Text("El servidor validará y registrará la reversión."); OutlinedTextField(reason, { reason = it }, label = { Text("Motivo obligatorio") }); ErrorCard(operation.error); if (operation.loading) Loading() } },
            confirmButton = { TextButton({ vm.reverse(action.first, action.second, reason) }, enabled = reason.isNotBlank() && !operation.loading) { Text("Confirmar reversión") } },
            dismissButton = { TextButton({ reversal = null }, enabled = !operation.loading) { Text("Cerrar") } })
        LaunchedEffect(operation.value != null) { if (operation.value != null) reversal = null }
    }
}

@Composable private fun PaymentDialog(vm: CreditsViewModel, close: () -> Unit) {
    val statement by vm.statement.collectAsStateWithLifecycle()
    val payment by vm.payment.collectAsStateWithLifecycle()
    val pending by vm.attempt.collectAsStateWithLifecycle()
    var amount by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf("CASH") }
    var notes by rememberSaveable { mutableStateOf("") }
    var review by remember { mutableStateOf<CreditPaymentRequest?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val c = statement.value?.customer
    LaunchedEffect(payment.value) { if (payment.value != null) close() }
    // A successful response closes the entry form; the account keeps the confirmed receipt.
    if (payment.value != null) return
    FullScreenDialog({ if (!payment.loading && pending == null) close() }) {
        LazyColumn(Modifier.testTag("credit.payment.list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Registrar abono", style = MaterialTheme.typography.headlineSmall); Text(c?.fullName.orEmpty()); c?.let { Text("Saldo actual ${it.balance.mxn()}") } }
            if (pending == null) {
                item { OutlinedTextField(amount, { amount = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Cantidad a abonar") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !payment.loading) }
                item { listOf("CASH", "TRANSFER", "CARD").forEach { choice -> Row { RadioButton(method == choice, { method = choice }, enabled = !payment.loading); TextButton({ method = choice }, enabled = !payment.loading) { Text(optionLabel(choice)) } } } }
                item { OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Nota (opcional)") }, enabled = !payment.loading) }
            } else item { Text("Abono pendiente de confirmar: ${pending!!.request.amount.mxn()} · ${optionLabel(pending!!.request.method)}. Reintenta la misma operación.") }
            item { ErrorCard(payment.error); error?.let { ErrorCard(Failure(it)) } }
            if (payment.loading) item { Loading() }
            item { Action(if (pending != null) "Reintentar el mismo abono" else "Confirmar abono", !payment.loading && (pending != null || c != null)) {
                if (pending != null) vm.pay() else try { review = vm.validatePayment(amount, method, notes) } catch (e: IllegalArgumentException) { error = e.message }
            } }
            if (pending == null) item { TextButton(close, enabled = !payment.loading) { Text("Cancelar") } }
        }
    }
    review?.let { request -> AlertDialog({ review = null }, title = { Text("Confirmar abono") },
        text = { Column { Text(c?.fullName.orEmpty()); c?.let { Text("Saldo anterior ${it.balance.mxn()}"); Text("Abono ${request.amount.mxn()}"); Text("Saldo resultante estimado ${(it.balance - request.amount).mxn()}") }; Text(optionLabel(request.method)) } },
        confirmButton = { TextButton({ review = null; vm.pay(request) }) { Text("Confirmar") } }, dismissButton = { TextButton({ review = null }) { Text("Cancelar") } }) }
}
