package com.mauspancho.tiendapos.data.dto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import java.math.BigDecimal

@OptIn(ExperimentalSerializationApi::class)
object DecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("Decimal", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigDecimal) {
        if (encoder is JsonEncoder) encoder.encodeJsonElement(JsonUnquotedLiteral(value.toPlainString())) else encoder.encodeString(value.toPlainString())
    }
    override fun deserialize(decoder: Decoder): BigDecimal =
        (if (decoder is JsonDecoder) decoder.decodeJsonElement().jsonPrimitive.content else decoder.decodeString()).toBigDecimal()
}
typealias Decimal = @Serializable(with = DecimalSerializer::class) BigDecimal
@Serializable data class Health(val status: String, val application: String, val apiVersion: String, val serverVersion: String) {
    fun validate(): Health {
        require(application == "Tienda POS" && status == "UP") { "Respuesta inválida: no es un servidor Tienda POS disponible." }
        require(apiVersion == "v1") { "API incompatible: esta aplicación requiere v1." }
        require(serverVersion.isNotBlank()) { "Respuesta inválida del servidor." }
        return this
    }
}
@Serializable data class User(val id: Long, val username: String, val displayName: String, val email: String? = null,
    val enabled: Boolean, val roles: List<String>)
@Serializable data class LoginRequest(val username: String, val password: String)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class Session(val accessToken: String, val refreshToken: String, val tokenType: String,
    val expiresIn: Long, val user: User)
@Serializable data class StoredSession(val server: String, val session: Session)
@Serializable data class ApiError(val status: Int, val error: String, val message: String, val fieldErrors: Map<String, String> = emptyMap())
@Serializable data class Page<T>(val content: List<T>, val page: Int, val size: Int, val totalElements: Long, val totalPages: Int)
@Serializable data class Ref(val id: Long, val name: String)
@Serializable data class Product(val id: Long, val code: String, val barcode: String? = null, val name: String,
    val brand: String? = null, val presentation: String? = null, val imageUrl: String? = null,
    val description: String? = null, val category: Ref? = null, val purchaseCost: Decimal,
    val salePrice: Decimal, val currentStock: Decimal, val minimumStock: Decimal, val unit: String,
    val supplier: Ref? = null, val tax: Decimal, val active: Boolean, val promoted: Boolean,
    val promotionOrder: Int? = null, val promocionWhatsapp: Boolean, val lowStock: Boolean)
@Serializable data class ProductRequest(val code: String, val barcode: String?, val name: String, val brand: String?,
    val presentation: String?, val imageUrl: String?, val description: String?, val categoryId: Long?,
    val purchaseCost: Decimal, val salePrice: Decimal, val currentStock: Decimal, val minimumStock: Decimal,
    val unit: String, val supplierId: Long?, val tax: Decimal, val active: Boolean, val removeImage: Boolean = false)
@Serializable data class Enabled(val enabled: Boolean)
@Serializable data class Active(val active: Boolean)
@Serializable data class PosState(val cashOpen: Boolean, val cashSession: PosCash? = null, val paymentMethods: List<String>,
    val settlementTypes: List<String> = emptyList())
@Serializable data class PosCash(val id: Long, val openedAt: String, val openingAmount: Decimal)
@Serializable data class SaleLineRequest(val productId: Long, val quantity: Decimal)
@Serializable data class CheckoutRequest(val customerId: Long? = null, val discount: Decimal = BigDecimal.ZERO,
    val paymentMethod: String, val receivedAmount: Decimal, val items: List<SaleLineRequest>,
    @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val settlementType: String? = null)
@Serializable data class Payment(val method: String, val amount: Decimal, val receivedAmount: Decimal, val changeAmount: Decimal)
@Serializable data class SaleLine(val productId: Long, val productName: String, val quantity: Decimal,
    val unitPrice: Decimal, val unitCost: Decimal, val discount: Decimal, val subtotal: Decimal, val profit: Decimal)
@Serializable data class Sale(val id: Long, val folio: String, val saleDate: String, val cashier: Ref? = null,
    val customer: Ref? = null, val subtotal: Decimal, val discount: Decimal, val tax: Decimal,
    val total: Decimal, val status: String, val payment: Payment? = null, val items: List<SaleLine>,
    val settlementType: String = "IMMEDIATE", val amountPaid: Decimal? = null, val pendingBalance: Decimal? = null)
@Serializable data class Dashboard(val todaySales: Decimal, val grossProfit: Decimal, val soldUnits: Decimal,
    val tickets: Long, val lowStockCount: Long, val todayExpenses: Decimal, val inventoryInvestment: Decimal,
    val dailySales: List<DailySale>, val topProducts: List<TopProduct>)
@Serializable data class DailySale(val date: String, val sales: Decimal)
@Serializable data class TopProduct(val product: String, val quantity: Decimal, val sales: Decimal, val profit: Decimal)
@Serializable data class CurrentCash(val open: Boolean, val session: CashSession? = null)
@Serializable data class CashSession(val id: Long, val cashierId: Long? = null, val cashierName: String? = null,
    val openedAt: String, val closedAt: String? = null, val openingAmount: Decimal, val expectedAmount: Decimal?,
    val countedAmount: Decimal? = null, val differenceAmount: Decimal? = null, val open: Boolean)
@Serializable data class OpenCash(val openingAmount: Decimal)
@Serializable data class CloseCash(val countedAmount: Decimal)
