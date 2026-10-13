package com.mauspancho.tiendapos.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mauspancho.tiendapos.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DashboardScreen(vm: DashboardViewModel, name: String, pos: () -> Unit, profit: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    PullToRefreshBox(state.loading, vm::refresh, Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Hola, $name", style = MaterialTheme.typography.headlineMedium) }
            item { TextButton(profit) { Text("Análisis de ganancias") } }
            item { ErrorCard(state.error, vm::refresh) }
            if (state.loading && state.value == null) item { Loading() }
            state.value?.let { data ->
                val cards = listOf("Ventas de hoy" to data.todaySales.money(), "Ganancia bruta" to data.grossProfit.money(), "Tickets" to data.tickets.toString(),
                    "Unidades vendidas" to data.soldUnits.toPlainString(), "Productos con poco stock" to data.lowStockCount.toString(),
                    "Gastos de hoy" to data.todayExpenses.money(), "Inversión en inventario" to data.inventoryInvestment.money())
                cards.forEach { (label, value) -> item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge); Text(value, style = MaterialTheme.typography.headlineMedium)
                } } } }
                item { Action("Nueva venta", onClick = pos) }
                item { Text("Productos destacados", style = MaterialTheme.typography.titleLarge) }
                data.topProducts.forEach { product -> item { ListItem(headlineContent = { Text(product.product) }, supportingContent = { Text("${product.quantity} unidades · ${product.sales.money()}") }) } }
                if (data.topProducts.isEmpty()) item { Empty() }
                item { Text("Ventas diarias", style = MaterialTheme.typography.titleLarge) }
                data.dailySales.forEach { day -> item { ListItem(headlineContent = { Text(day.date) }, trailingContent = { Text(day.sales.money()) }) } }
            }
        }
    }
}
