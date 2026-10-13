package com.mauspancho.tiendapos.ui.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mauspancho.tiendapos.ui.components.*
import java.math.BigDecimal
import com.mauspancho.tiendapos.ui.credits.*

@Composable fun PosScreen(vm: PosViewModel, admin: Boolean, openCash: () -> Unit, register: (String) -> Unit, ticket: (String) -> Unit,
    customerPicker: CustomerPickerViewModel? = null) {
    val cart by vm.cart.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sale by vm.sale.collectAsStateWithLifecycle()
    val attempt by vm.attempt.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val unknown by vm.unknownBarcode.collectAsStateWithLifecycle()
    val credit by vm.creditMode.collectAsStateWithLifecycle()
    val customer by vm.creditCustomer.collectAsStateWithLifecycle()
    val account by vm.saleAccount.collectAsStateWithLifecycle()
    val restoring by vm.restoring.collectAsStateWithLifecycle()
    var selectingCustomer by rememberSaveable { mutableStateOf(false) }
    var confirmCredit by remember { mutableStateOf(false) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    if (restoring) { Column(Modifier.padding(16.dp)) { Loading(); ErrorCard(operation.error) }; return }
    if (scanning) {
        ScanSound(vm.scanAdded)
        Column(Modifier.fillMaxSize()) {
            Text("${cart.lines.sumOf { it.quantity.toInt() }} unidades · ${cart.total.money()}", Modifier.padding(16.dp))
            ErrorCard(operation.error)
            unknown?.let { code -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("Producto no registrado: $code")
                if (admin) TextButton({ scanning = false; register(code); vm.unknownBarcode.value = null }) { Text("Registrar producto") }
                TextButton({ vm.unknownBarcode.value = null }) { Text("Continuar escaneando") }
            } } }
            ScannerScreen(vm::scan) { scanning = false }
        }
        return
    }
    sale.value?.let { done ->
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(if (done.settlementType == "CREDIT") "Crédito registrado" else "Venta realizada", style = MaterialTheme.typography.headlineMedium) }
            item { Text("Folio ${done.folio}"); Text(creditDate(done.saleDate)); Text("Total ${done.total.mxn()}")
                if (done.settlementType == "CREDIT") { Text(done.customer?.name.orEmpty()); Text("Pendiente de este crédito ${done.pendingBalance?.mxn().orEmpty()}") }
                else Text("Cambio ${done.payment?.changeAmount?.money().orEmpty()}") }
            if (done.settlementType == "CREDIT") {
                if (account.loading) item { Loading() }
                item { ErrorCard(account.error, vm::refreshSaleAccount) }
                account.value?.let { statement -> item { Text("Saldo actualizado del cliente ${statement.customer.balance.mxn()}") } }
            }
            item { Action("Nueva venta") { vm.newSale() } }
            item { Action("Ver ticket") { ticket(done.folio) } }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("pos.list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ErrorCard(state.error, vm::refresh); ErrorCard(operation.error) }
        if (state.loading) item { Loading() }
        if (state.value?.cashOpen == false) item { Card { Column(Modifier.padding(16.dp)) { Text("Necesitas una caja abierta para cobrar."); TextButton(openCash) { Text("Ir a Caja") } } } }
        if (attempt == null && !sale.loading) {
            item { OutlinedTextField(query, vm::search, Modifier.fillMaxWidth(), label = { Text("Buscar nombre, marca o código") }, singleLine = true) }
            item { Action("Escanear código de barras") { scanning = true } }
            if (results.loading) item { Loading() }
            item { ErrorCard(results.error) { vm.search(query) } }
            results.value?.let { matches ->
                if (matches.isEmpty()) item { Empty("No se encontraron productos") }
                items(matches, key = { "match.${it.id}" }) { product -> Card(onClick = { if (vm.add(product)) vm.search("") }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(product.name) }, supportingContent = { Text(listOfNotNull(product.brand, product.presentation, product.code).joinToString(" · ")) }, trailingContent = { Text(product.salePrice.money()) })
                } }
            }
        }
        item { Text("Carrito", style = MaterialTheme.typography.titleLarge) }
        if (cart.lines.isEmpty() && attempt == null) item { Empty("Agrega un producto para comenzar") }
        items(cart.lines, key = { it.product.id }) { line -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(line.product.name, style = MaterialTheme.typography.titleMedium)
            Text("${line.product.salePrice.money()} × ${line.quantity.toPlainString()} = ${line.subtotal.money()}")
            if (credit) Text("Existencias disponibles ${line.product.currentStock.toPlainString()} ${line.product.unit}")
            if (attempt == null && !sale.loading) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.quantity(line.product.id, line.quantity - BigDecimal.ONE) }, modifier = Modifier.semantics { contentDescription = "Disminuir cantidad de ${line.product.name}" }) { Text("−", modifier = Modifier.padding(4.dp)) }
                OutlinedButton({ vm.quantity(line.product.id, line.quantity + BigDecimal.ONE) }, modifier = Modifier.semantics { contentDescription = "Incrementar cantidad de ${line.product.name}" }) { Text("+", modifier = Modifier.padding(4.dp)) }
                TextButton({ vm.remove(line.product.id) }) { Text("Quitar") }
            }
            if (attempt == null && !sale.loading) {
                var entered by rememberSaveable(line.product.id, line.quantity.toPlainString()) { mutableStateOf(line.quantity.toPlainString()) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(entered, { entered = it }, Modifier.weight(1f), label = { Text("Cantidad") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    TextButton({ entered.toBigDecimalOrNull()?.takeIf { it >= BigDecimal("0.001") }?.let { vm.quantity(line.product.id, it) } }) { Text("Aplicar") }
                }
            }
        } } }
        item { Text("Total estimado ${cart.total.money()}", style = MaterialTheme.typography.headlineSmall); Text("El servidor confirmará impuestos, descuentos y total final.", style = MaterialTheme.typography.bodySmall) }
        if (attempt == null && !sale.loading && customerPicker != null && "CREDIT" in state.value?.settlementTypes.orEmpty()) item {
            Row { FilterChip(!credit, { vm.mode(false) }, { Text("Efectivo") }); Spacer(Modifier.width(8.dp)); FilterChip(credit, { vm.mode(true) }, { Text("Crédito") }) }
        }
        if (credit) {
            item { Text("Venta a crédito") }
            if (attempt == null) {
                item { Action(customer.value?.customer?.fullName ?: "Seleccionar cliente", !sale.loading) { selectingCustomer = true }; ErrorCard(customer.error) }
                if (customer.loading) item { Loading() }
                customer.value?.let { statement -> item { Text("Adeudo anterior ${statement.customer.balance.mxn()}"); Text("Saldo proyectado estimado ${(statement.customer.balance + cart.total).mxn()}") } }
            }
        } else item { Text("Pago en efectivo") }
        if (attempt != null) item { Text("Hay un cobro pendiente de confirmar. Reintenta la misma operación para conocer el resultado.") }
        item { ErrorCard(sale.error) }
        if (sale.loading) item { Loading() }
        item { Action(if (attempt != null) "Reintentar el mismo cobro" else if (credit) "Confirmar crédito" else "Cobrar", !sale.loading &&
            (attempt != null || (cart.lines.isNotEmpty() && state.value?.cashOpen == true && (!credit || (!customer.loading && customer.value != null && "CREDIT" in state.value?.settlementTypes.orEmpty()))))) {
                if (credit && attempt == null) confirmCredit = true else vm.checkout()
            } }
    }
    if (selectingCustomer && customerPicker != null) CustomerPicker(customerPicker, admin, { vm.selectCustomer(it.id) }) { selectingCustomer = false }
    if (confirmCredit) AlertDialog({ confirmCredit = false }, title = { Text("Confirmar venta a crédito") },
        text = { Column { Text(customer.value?.customer?.fullName.orEmpty()); Text("Nuevo crédito estimado ${cart.total.mxn()}"); customer.value?.let { Text("Saldo proyectado estimado ${(it.customer.balance + cart.total).mxn()}") } } },
        confirmButton = { TextButton({ confirmCredit = false; vm.checkout() }) { Text("Confirmar") } }, dismissButton = { TextButton({ confirmCredit = false }) { Text("Cancelar") } })
}
