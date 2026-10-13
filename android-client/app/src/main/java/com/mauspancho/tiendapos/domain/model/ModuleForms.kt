package com.mauspancho.tiendapos.domain.model

import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.time.LocalDate

enum class InputKind { TEXT, DECIMAL, ID, BOOLEAN, DATE, CHOICE, REFERENCE, PASSWORD, ROLES }
data class FormField(val key: String, val label: String, val kind: InputKind = InputKind.TEXT,
    val required: Boolean = false, val initial: String = "", val options: List<String> = emptyList(),
    val reference: String? = null, val minimum: String? = null)
data class FormSpec(val title: String, val path: String, val fields: List<FormField>, val edit: Boolean = false,
    val idempotent: Boolean = false)
private fun text(key: String, label: String, required: Boolean = false) = FormField(key, label, required = required)
private fun money(key: String, label: String, minimum: String = "0.00", initial: String = "") =
    FormField(key, label, InputKind.DECIMAL, true, initial, minimum = minimum)
private fun bool(key: String, label: String, initial: Boolean = false) = FormField(key, label, InputKind.BOOLEAN, initial = initial.toString())
private fun ref(key: String, label: String, path: String, required: Boolean = false) = FormField(key, label, InputKind.REFERENCE, required, reference = path)
private fun choice(key: String, label: String, options: List<String>) = FormField(key, label, InputKind.CHOICE, true, options.first(), options)
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object ModuleForms {
    fun create(module: String, initialBarcode: String? = null): FormSpec? = when (module) {
        "products" -> FormSpec("Registrar producto", "products", listOf(text("code", "Código", true),
            text("barcode", "Código de barras").copy(initial = initialBarcode.orEmpty()), text("name", "Nombre", true),
            text("brand", "Marca"), text("presentation", "Presentación"), text("description", "Descripción"),
            ref("categoryId", "Categoría", "categories"), ref("supplierId", "Proveedor", "suppliers"),
            money("purchaseCost", "Costo", initial = "0"), money("salePrice", "Precio", "0.01"),
            money("currentStock", "Stock inicial", "0", "0"), money("minimumStock", "Stock mínimo", "0", "0"),
            choice("unit", "Unidad", listOf("PIEZA", "KG", "GRAMO", "LITRO", "ML", "PAQUETE", "CAJA")),
            money("tax", "Impuesto", initial = "0"), bool("active", "Activo", true), bool("removeImage", "Quitar imagen")))
        "purchases" -> FormSpec("Registrar compra", "purchases", listOf(ref("productId", "Producto", "products/search", true),
            ref("supplierId", "Proveedor", "suppliers"), money("quantity", "Cantidad", "0.001", "1"),
            money("unitCost", "Costo unitario"), text("externalFolio", "Folio del proveedor"),
            choice("fundingSource", "Origen del dinero", listOf("BUSINESS_CASH", "OWNER_CAPITAL", "SUPPLIER_CREDIT", "OTHER", "UNKNOWN")),
            bool("updateProductCost", "Actualizar costo"), text("notes", "Notas")), idempotent = true)
        "inventory/movements" -> FormSpec("Ajustar inventario", "inventory/adjustments", listOf(ref("productId", "Producto", "products/search", true),
            choice("movementType", "Movimiento", listOf("ADJUSTMENT_IN", "ADJUSTMENT_OUT", "INITIAL_STOCK")),
            money("quantity", "Cantidad", "0.001"), money("unitCost", "Costo unitario", initial = "0"), text("notes", "Motivo", true)))
        "expenses" -> FormSpec("Registrar gasto", "expenses", listOf(text("concept", "Concepto", true),
            ref("categoryId", "Categoría", "expenses/categories", true), money("amount", "Importe", "0.01"),
            FormField("expenseDate", "Fecha", InputKind.DATE, true, LocalDate.now().toString()), text("notes", "Notas")))
        "categories" -> FormSpec("Nueva categoría", "categories", listOf(text("name", "Nombre", true),
            text("description", "Descripción"), bool("active", "Activa", true)))
        "suppliers" -> FormSpec("Nuevo proveedor", "suppliers", listOf(text("name", "Nombre", true), text("companyName", "Empresa"),
            text("phone", "Teléfono"), text("email", "Correo"), text("address", "Dirección"), text("taxId", "RFC"), text("notes", "Notas"), bool("active", "Activo", true)))
        "users" -> FormSpec("Nuevo usuario", "users", listOf(text("username", "Usuario", true),
            FormField("password", "Contraseña inicial", InputKind.PASSWORD, true), text("firstName", "Nombre", true),
            text("lastName", "Apellidos", true), text("email", "Correo"),
            FormField("roles", "Roles", InputKind.ROLES, true, "CAJERO", listOf("ADMIN", "CAJERO")), bool("active", "Activo", true)))
        "finances/capital" -> FormSpec("Movimiento de capital", "finances/capital", listOf(
            FormField("movementDate", "Fecha", InputKind.DATE, true, LocalDate.now().toString()),
            choice("type", "Tipo", listOf("INITIAL_INVESTMENT", "OWNER_CONTRIBUTION", "OWNER_WITHDRAWAL", "CAPITAL_ADJUSTMENT")),
            money("amount", "Importe", "0.01"), text("description", "Descripción")))
        "settings" -> FormSpec("Datos de la tienda", "settings", listOf(text("storeName", "Nombre de la tienda", true),
            text("address", "Dirección"), text("phone", "Teléfono"), text("taxId", "RFC"), text("currency", "Moneda", true),
            text("currencySymbol", "Símbolo de moneda", true), text("timezone", "Zona horaria", true),
            money("defaultTax", "Impuesto predeterminado", initial = "0"), bool("catalogEnabled", "Catálogo público"),
            text("catalogTitle", "Título del catálogo"), text("catalogSubtitle", "Subtítulo del catálogo"), text("promotionTitle", "Título de promociones"),
            bool("negativeStockAllowed", "Permitir stock negativo"), bool("removeLogo", "Quitar logo")), edit = true)
        else -> null
    }
    fun edit(module: String, id: String, source: JsonObject): FormSpec? {
        if (module !in listOf("products", "categories", "suppliers", "users", "settings")) return null
        val original = create(module) ?: return null
        return original.copy(title = "Editar", path = if (module == "settings") module else "$module/$id", edit = true,
            fields = original.fields.filterNot { it.key == "currentStock" || (module == "users" && it.key == "password") }.map { field ->
                val key = if (field.key == "active" && module == "users") "enabled" else field.key
                val entry = source[key]
                val value = when {
                    field.kind == InputKind.REFERENCE -> source[field.key.removeSuffix("Id")]?.let { (it as? JsonObject)?.get("id") }?.jsonPrimitive?.content
                    field.kind == InputKind.ROLES -> (entry as? JsonArray)?.joinToString(",") { it.jsonPrimitive.content }
                    entry is JsonPrimitive && entry !is JsonNull -> entry.content
                    else -> null
                }
                field.copy(initial = value ?: field.initial)
            })
    }
    fun payload(spec: FormSpec, values: Map<String, String>, source: JsonObject? = null): JsonObject = buildJsonObject {
        spec.fields.forEach { field ->
            val value = values[field.key]?.trim() ?: field.initial
            require(!field.required || value.isNotBlank()) { "${field.label}: este campo es obligatorio." }
            val element: JsonElement = when (field.kind) {
                InputKind.DECIMAL -> {
                    val decimal = value.toBigDecimalOrNull() ?: throw IllegalArgumentException("${field.label}: ingresa un número válido.")
                    require(field.minimum == null || decimal >= BigDecimal(field.minimum)) { "${field.label}: el mínimo es ${field.minimum}." }
                    JsonUnquotedLiteral(decimal.toPlainString())
                }
                InputKind.ID, InputKind.REFERENCE -> if (value.isBlank()) JsonNull else JsonPrimitive(value.toLongOrNull()
                    ?: throw IllegalArgumentException("Selecciona ${field.label.lowercase()}."))
                InputKind.BOOLEAN -> JsonPrimitive(value == "true")
                InputKind.DATE -> { require(runCatching { LocalDate.parse(value) }.isSuccess) { "${field.label}: usa AAAA-MM-DD." }; JsonPrimitive(value) }
                InputKind.CHOICE -> { require(value in field.options) { "Selecciona ${field.label.lowercase()}." }; JsonPrimitive(value) }
                InputKind.ROLES -> {
                    val roles = value.split(',').filter { it in field.options }
                    require(roles.isNotEmpty()) { "Selecciona al menos un rol." }
                    JsonArray(roles.map { JsonPrimitive(it) })
                }
                InputKind.PASSWORD -> JsonPrimitive(values[field.key].orEmpty())
                else -> if (value.isBlank() && !field.required) JsonNull else JsonPrimitive(value)
            }
            put(field.key, element)
        }
        if (spec.path.startsWith("products")) {
            put("imageUrl", source?.get("imageUrl") ?: JsonNull)
            if (spec.edit) put("currentStock", source?.get("currentStock") ?: JsonPrimitive(0))
        }
        if (spec.path.startsWith("users/") && spec.edit) put("password", JsonNull)
    }
}
fun optionLabel(value: String): String = mapOf("PIEZA" to "Pieza", "KG" to "Kilogramo", "GRAMO" to "Gramo", "LITRO" to "Litro", "ML" to "Mililitro",
    "PAQUETE" to "Paquete", "CAJA" to "Caja", "BUSINESS_CASH" to "Caja / ventas", "OWNER_CAPITAL" to "Capital propietario",
    "SUPPLIER_CREDIT" to "Crédito proveedor", "OTHER" to "Otro", "UNKNOWN" to "Sin clasificar", "ADJUSTMENT_IN" to "Entrada",
    "ADJUSTMENT_OUT" to "Salida", "INITIAL_STOCK" to "Stock inicial", "INITIAL_INVESTMENT" to "Inversión inicial",
    "OWNER_CONTRIBUTION" to "Aportación", "OWNER_WITHDRAWAL" to "Retiro", "CAPITAL_ADJUSTMENT" to "Ajuste de capital",
    "CASH" to "Efectivo", "CARD" to "Tarjeta", "TRANSFER" to "Transferencia", "ADMIN" to "Administrador", "CAJERO" to "Cajero",
    "PURCHASE" to "Compra", "SALE" to "Venta", "SALE_RETURN" to "Devolución venta", "PURCHASE_RETURN" to "Devolución compra")[value] ?: value
