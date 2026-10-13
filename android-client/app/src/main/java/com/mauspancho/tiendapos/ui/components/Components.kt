package com.mauspancho.tiendapos.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import com.mauspancho.tiendapos.data.api.*
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.math.RoundingMode

fun BigDecimal.money(): String = setScale(2, RoundingMode.HALF_UP).toPlainString()
@Composable fun ErrorCard(error: Failure?, retry: (() -> Unit)? = null) {
    if (error == null) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(error.message)
            error.fields.values.distinct().forEach { Text(it) }
            if (retry != null) TextButton(onClick = retry) { Text("Reintentar") }
        }
    }
}
@Composable fun Loading() { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
@Composable fun Empty(message: String = "Sin resultados") { Text(message, Modifier.fillMaxWidth().padding(24.dp)) }
@Composable fun Action(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled) { Text(label) }
}
@Composable fun PagingStatus(state: LoadState, json: Json, retry: () -> Unit) {
    when (state) { is LoadState.Loading -> Loading(); is LoadState.Error -> ErrorCard(state.error.failure(json), retry); else -> Unit }
}
// Only known, non-sensitive contract fields are displayed. Unknown fields are deliberately omitted.
val labels = mapOf("name" to "Nombre", "displayName" to "Nombre", "username" to "Usuario", "email" to "Correo", "roles" to "Roles",
    "code" to "Código", "barcode" to "Código de barras", "brand" to "Marca", "presentation" to "Presentación", "description" to "Descripción",
    "category" to "Categoría", "supplier" to "Proveedor", "salePrice" to "Precio", "purchaseCost" to "Costo", "currentStock" to "Stock",
    "minimumStock" to "Stock mínimo", "unit" to "Unidad", "tax" to "Impuesto", "active" to "Activo", "enabled" to "Activo",
    "promoted" to "Promoción pública", "promocionWhatsapp" to "Promoción WhatsApp", "lowStock" to "Stock bajo",
    "folio" to "Folio", "saleDate" to "Fecha", "cashier" to "Cajero", "customer" to "Cliente", "subtotal" to "Subtotal", "discount" to "Descuento",
    "total" to "Total", "status" to "Estado", "settlementType" to "Tipo de cobro", "amountPaid" to "Abonado", "pendingBalance" to "Pendiente", "payment" to "Pago", "items" to "Productos", "productName" to "Producto",
    "quantity" to "Cantidad", "unitPrice" to "Precio unitario", "unitCost" to "Costo unitario", "profit" to "Ganancia",
    "method" to "Método", "amount" to "Importe", "receivedAmount" to "Recibido", "changeAmount" to "Cambio", "purchaseDate" to "Fecha",
    "externalFolio" to "Folio proveedor", "fundingSource" to "Origen del dinero", "notes" to "Notas", "user" to "Usuario",
    "type" to "Tipo", "previousStock" to "Stock anterior", "newStock" to "Stock resultante", "createdAt" to "Fecha", "reversed" to "Retirado",
    "referenceType" to "Origen", "concept" to "Concepto", "expenseDate" to "Fecha", "companyName" to "Empresa", "phone" to "Teléfono",
    "address" to "Dirección", "taxId" to "RFC", "session" to "Sesión", "open" to "Abierta", "cashierName" to "Cajero", "openedAt" to "Apertura",
    "closedAt" to "Cierre", "openingAmount" to "Monto inicial", "expectedAmount" to "Esperado", "countedAmount" to "Contado",
    "differenceAmount" to "Diferencia", "movements" to "Movimientos", "movementDate" to "Fecha", "period" to "Periodo", "range" to "Rango",
    "from" to "Desde", "to" to "Hasta", "sales" to "Ventas", "costOfGoodsSold" to "Costo vendido", "grossProfit" to "Ganancia bruta",
    "expenses" to "Gastos", "netProfit" to "Utilidad neta", "purchases" to "Compras", "reinvestment" to "Reinversión",
    "ownerContributions" to "Aportaciones", "ownerWithdrawals" to "Retiros", "tickets" to "Tickets", "soldUnits" to "Unidades vendidas",
    "today" to "Hoy", "initialInvestment" to "Inversión inicial", "additionalContributions" to "Aportaciones adicionales",
    "capitalContributedTotal" to "Capital aportado", "ownerWithdrawalsTotal" to "Retiros acumulados", "retainedProfit" to "Ganancia retenida",
    "netProfitAccumulated" to "Utilidad acumulada", "capitalInsideBusiness" to "Capital en negocio", "inventoryCost" to "Costo inventario",
    "inventorySaleValue" to "Valor de venta inventario", "inventoryPotentialProfit" to "Ganancia potencial", "recoveryPercent" to "Recuperación (%)",
    "recoveryRemaining" to "Pendiente de recuperar", "cashIn" to "Entradas", "cashOut" to "Salidas", "cashFlow" to "Flujo de efectivo",
    "daily" to "Histórico diario", "date" to "Fecha", "topProducts" to "Productos destacados", "product" to "Producto",
    "inventoryValue" to "Inventario valorizado", "result" to "Resultado", "storeName" to "Tienda", "currency" to "Moneda",
    "currencySymbol" to "Símbolo", "timezone" to "Zona horaria", "defaultTax" to "Impuesto predeterminado", "catalogEnabled" to "Catálogo público",
    "catalogTitle" to "Título del catálogo", "catalogSubtitle" to "Subtítulo", "promotionTitle" to "Título de promociones",
    "negativeStockAllowed" to "Stock negativo permitido", "apiVersion" to "Versión API", "serverVersion" to "Versión servidor",
    "products" to "Rentabilidad por producto", "marginPercent" to "Margen (%)", "monthlyReinvestment" to "Reinversión mensual",
    "monthlyCapital" to "Capital mensual", "label" to "Periodo", "value" to "Importe", "secondary" to "Importe secundario",
    "tertiary" to "Importe adicional", "selectedDate" to "Fecha seleccionada", "selectedDateProfit" to "Ganancia del día",
    "selectedDateCostAdjustment" to "Ajustes de costo del día", "periodLabel" to "Periodo", "periodProfit" to "Ganancia del periodo",
    "periodCostAdjustment" to "Ajustes de costo del periodo", "points" to "Ganancias por periodo")
fun JsonObject.title(): String = listOf("name", "displayName", "folio", "externalFolio", "productName", "concept", "cashierName", "description")
    .firstNotNullOfOrNull { (get(it) as? JsonPrimitive)?.contentOrNull?.takeIf { value -> value.isNotBlank() } }
    ?: "Registro ${get("id")?.jsonPrimitive?.content.orEmpty()}"
@Composable fun NativeDetails(value: JsonElement, depth: Int = 0, fieldLabels: Map<String, String> = labels) {
    when (value) {
        is JsonObject -> value.forEach { (key, entry) ->
            val label = fieldLabels[key]
            if (label != null && entry !is JsonNull) {
                if (entry is JsonPrimitive) {
                    val rendered = when (entry.booleanOrNull) { true -> "Sí"; false -> "No"; null -> com.mauspancho.tiendapos.domain.model.optionLabel(entry.content) }
                    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(rendered, style = MaterialTheme.typography.bodyLarge)
                    }
                } else if (depth < 3) {
                    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
                    val nestedLabels = when (key) {
                        "monthlyCapital" -> fieldLabels + mapOf("value" to "Capital aportado", "secondary" to "Utilidad acumulada", "tertiary" to "Retiros acumulados")
                        "monthlyReinvestment" -> (fieldLabels - "secondary" - "tertiary") + ("value" to "Reinversión")
                        else -> fieldLabels
                    }
                    Column(Modifier.padding(start = 12.dp)) { NativeDetails(entry, depth + 1, nestedLabels) }
                }
            }
        }
        is JsonArray -> value.forEach { entry -> if (entry is JsonPrimitive) Text(com.mauspancho.tiendapos.domain.model.optionLabel(entry.content)) else {
            HorizontalDivider(Modifier.padding(vertical = 8.dp)); NativeDetails(entry, depth, fieldLabels)
        } }
        else -> Unit
    }
}
