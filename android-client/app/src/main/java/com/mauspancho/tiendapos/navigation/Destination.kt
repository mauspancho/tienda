package com.mauspancho.tiendapos.navigation

enum class Destination(val label: String, val path: String?, val admin: Boolean = false, val paged: Boolean = false) {
    DASHBOARD("Inicio", "dashboard"), POS("Punto de venta", "pos"), PRODUCTS("Productos", "products", paged = true),
    PURCHASES("Compras", "purchases", true, true), INVENTORY("Inventario", "inventory/movements", true, true),
    SALES("Ventas", "sales", paged = true), TICKETS("Tickets", "sales", paged = true), CASH("Caja", "cash/current"), CREDITS("Créditos", "credits"),
    EXPENSES("Gastos", "expenses", true, true), FINANCES("Finanzas", "finances", true), REPORTS("Reportes", "reports", true),
    CATEGORIES("Categorías", "categories", true, true), SUPPLIERS("Proveedores", "suppliers", true, true),
    USERS("Usuarios", "users", true, true), WHATSAPP("WhatsApp", "whatsapp", true), SETTINGS("Configuración", null);
    fun allowed(roles: List<String>): Boolean = if (this == SETTINGS) true else
        if (admin) "ADMIN" in roles else roles.any { it == "ADMIN" || it == "CAJERO" }
}
