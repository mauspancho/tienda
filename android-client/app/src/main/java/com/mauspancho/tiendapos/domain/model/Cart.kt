package com.mauspancho.tiendapos.domain.model

import com.mauspancho.tiendapos.data.dto.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@kotlinx.serialization.Serializable data class CartLine(val product: Product, val quantity: Decimal) {
    val subtotal: BigDecimal get() = product.salePrice.multiply(quantity)
}
data class Cart(val lines: List<CartLine> = emptyList()) {
    // The checkout API rounds each line to cents before adding them.
    val total: BigDecimal get() = lines.fold(BigDecimal("0.00")) { sum, line -> sum + line.subtotal.setScale(2, RoundingMode.HALF_UP) }
    fun add(product: Product): Cart {
        val old = lines.find { it.product.id == product.id }
        return if (old == null) copy(lines = lines + CartLine(product, BigDecimal.ONE)) else quantity(product.id, old.quantity + BigDecimal.ONE)
    }
    fun quantity(id: Long, quantity: BigDecimal) = copy(lines = if (quantity <= BigDecimal.ZERO) lines.filterNot { it.product.id == id }
        else lines.map { if (it.product.id == id) it.copy(quantity = quantity) else it })
    fun remove(id: Long) = copy(lines = lines.filterNot { it.product.id == id })
}
@kotlinx.serialization.Serializable data class CheckoutAttempt(val key: String, val request: CheckoutRequest) {
    companion object {
        fun create(cart: Cart, method: String, received: BigDecimal): CheckoutAttempt {
            require(cart.lines.isNotEmpty()) { "Agrega productos al carrito." }
            return CheckoutAttempt(UUID.randomUUID().toString(), CheckoutRequest(paymentMethod = method,
                receivedAmount = received, items = cart.lines.map { SaleLineRequest(it.product.id, it.quantity) }))
        }
    }
}
class BarcodeGate(private val interval: Long = 1200) {
    private var last: String? = null
    private var at = Long.MIN_VALUE
    @Synchronized fun accept(code: String, now: Long): Boolean {
        if (code == last && now - at < interval) return false
        last = code; at = now; return true
    }
}
