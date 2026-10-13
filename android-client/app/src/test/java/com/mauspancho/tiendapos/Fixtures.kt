package com.mauspancho.tiendapos

import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.data.storage.SessionStore
import java.math.BigDecimal

fun product(id: Long = 1, price: String = "12.30") = Product(id, "CODE$id", "1234567890123", "Producto $id",
    purchaseCost = BigDecimal("5.10"), salePrice = BigDecimal(price), currentStock = BigDecimal("25"), minimumStock = BigDecimal("2"),
    unit = "PIEZA", tax = BigDecimal.ZERO, active = true, promoted = false, promocionWhatsapp = false, lowStock = false)
fun session(token: String = "access", refresh: String = "refresh") = Session(token, refresh, "Bearer", 900,
    User(1, "fixture", "Usuario de pruebas", enabled = true, roles = listOf("CAJERO")))
class MemoryStore : SessionStore {
    @Volatile var value: StoredSession? = null
    override fun read() = value
    override fun write(value: StoredSession) { this.value = value }
    override fun clear() { value = null }
}
