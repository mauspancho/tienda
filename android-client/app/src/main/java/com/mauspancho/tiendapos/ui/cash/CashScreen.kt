package com.mauspancho.tiendapos.ui.cash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mauspancho.tiendapos.ui.components.*

@Composable fun CashScreen(vm: CashViewModel, history: () -> Unit, admin: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    var amount by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.loading) item { Loading() }
        item { ErrorCard(state.error, vm::refresh); ErrorCard(operation.error) }
        state.value?.let { cash ->
            item { Text(if (cash.open) "Caja abierta" else "Caja cerrada", style = MaterialTheme.typography.headlineMedium) }
            cash.session?.let { session -> item { Text("Apertura ${session.openedAt}"); Text("Monto inicial ${session.openingAmount.money()}"); session.expectedAmount?.let { Text("Esperado ${it.money()}") } } }
            item { OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text(if (cash.open) "Importe contado" else "Monto inicial") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !operation.loading) }
            item { Action(if (cash.open) "Cerrar caja" else "Abrir caja", !operation.loading && amount.isNotBlank()) { confirm = true } }
        }
        if (operation.loading) item { Loading() }
        item { TextButton(vm::refresh) { Text("Actualizar") } }
        if (admin) item { TextButton(history) { Text("Histórico de cajas y movimientos") } }
    }
    if (confirm) AlertDialog({ confirm = false }, title = { Text("Confirmar caja") }, text = { Text("Importe: $amount") },
        confirmButton = { TextButton({ confirm = false; vm.submit(amount) }) { Text("Confirmar") } }, dismissButton = { TextButton({ confirm = false }) { Text("Cancelar") } })
}
