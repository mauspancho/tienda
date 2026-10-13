package com.mauspancho.tiendapos

import com.mauspancho.tiendapos.domain.model.*
import com.mauspancho.tiendapos.navigation.Destination
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CartAndFormsTest {
    @Test fun cashTotalRoundsEachFractionalLineLikeTheServer() {
        val cart = Cart().add(product(1, "0.05")).add(product(2, "0.05"))
            .quantity(1, BigDecimal("0.5")).quantity(2, BigDecimal("0.5"))
        assertEquals(BigDecimal("0.06"), cart.total)
    }
    @Test fun addingSameProductIncrementsAndUsesExactDecimalMoney() {
        val cart = Cart().add(product()).add(product()).add(product(2, "0.10"))
        assertEquals(2, cart.lines.size); assertEquals(BigDecimal("24.70"), cart.total)
        assertEquals(BigDecimal("2"), cart.lines.first().quantity)
        val decreased = cart.quantity(1, BigDecimal.ONE)
        assertEquals(BigDecimal("12.40"), decreased.total)
        assertEquals(1, decreased.quantity(1, BigDecimal.ZERO).lines.size)
        assertTrue(decreased.remove(1).remove(2).lines.isEmpty())
    }
    @Test fun retryKeepsRequestAndUuid() {
        val attempt = CheckoutAttempt.create(Cart().add(product()), "CASH", BigDecimal("20"))
        val restored = Json.decodeFromString<CheckoutAttempt>(Json.encodeToString(attempt))
        assertEquals(attempt, restored)
        assertNotEquals(attempt.key, CheckoutAttempt.create(Cart().add(product()), "CASH", BigDecimal("20")).key)
    }
    @Test fun barcodeGateSuppressesDuplicatesOnlyWithinWindow() {
        val gate = BarcodeGate()
        assertTrue(gate.accept("ean", 0)); assertFalse(gate.accept("ean", 200)); assertTrue(gate.accept("ean", 1201)); assertTrue(gate.accept("another", 1202))
    }
    @Test fun navigationUsesRolesNotUsername() {
        assertTrue(Destination.POS.allowed(listOf("CAJERO"))); assertTrue(Destination.CASH.allowed(listOf("CAJERO")))
        assertFalse(Destination.INVENTORY.allowed(listOf("CAJERO"))); assertFalse(Destination.USERS.allowed(listOf("CAJERO")))
        assertTrue(Destination.entries.all { it.allowed(listOf("ADMIN")) })
        assertFalse(Destination.POS.allowed(listOf("PLATFORM_ADMIN"))); assertTrue(Destination.SETTINGS.allowed(emptyList()))
    }
    @Test fun editsKeepStockUnchangedAndDontEditPassword() {
        val source = Json.encodeToJsonElement(product()).jsonObject
        val spec = ModuleForms.edit("products", "1", source)!!
        assertTrue(spec.fields.none { it.key == "currentStock" })
        val payload = ModuleForms.payload(spec, spec.fields.associate { it.key to it.initial }, source)
        assertEquals(source["currentStock"], payload["currentStock"])
        assertTrue(ModuleForms.edit("users", "1", buildJsonObject {})!!.fields.none { it.kind == InputKind.PASSWORD })
    }
    @Test fun decimalAndDateValidationRejectBadInput() {
        val form = ModuleForms.create("expenses")!!
        assertTrue(runCatching { ModuleForms.payload(form, mapOf("concept" to "Luz", "categoryId" to "1", "amount" to "-2", "expenseDate" to "2026-10-01")) }.isFailure)
        assertTrue(runCatching { ModuleForms.payload(form, mapOf("concept" to "Luz", "categoryId" to "1", "amount" to "2", "expenseDate" to "2026-02-30")) }.isFailure)
    }
}
