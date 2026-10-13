package com.mauspancho.tiendapos.data.dto

import kotlinx.serialization.Serializable

@Serializable data class Customer(val id: Long, val name: String, val lastName: String? = null,
    val fullName: String, val phone: String? = null, val notes: String? = null, val active: Boolean,
    val registeredAt: String? = null)
@Serializable data class CustomerRequest(val name: String, val lastName: String? = null,
    val phone: String? = null, val notes: String? = null, val active: Boolean = true)
@Serializable data class CreditSummary(val pending: Decimal, val customersWithDebt: Long,
    val totalGranted: Decimal, val totalPaid: Decimal)
@Serializable data class CreditCustomer(val id: Long, val name: String, val lastName: String? = null,
    val fullName: String, val phone: String? = null, val active: Boolean, val balance: Decimal,
    val totalGranted: Decimal, val totalPaid: Decimal, val lastActivity: String? = null)
@Serializable data class CreditSaleLine(val product: String, val quantity: Decimal, val unitPrice: Decimal, val subtotal: Decimal)
@Serializable data class CreditSale(val id: Long, val folio: String, val date: String, val total: Decimal,
    val paid: Decimal, val balance: Decimal, val status: String, val items: List<CreditSaleLine>)
@Serializable data class CreditPayment(val id: Long, val date: String, val amount: Decimal, val method: String,
    val notes: String? = null, val status: String, val username: String)
@Serializable data class CreditCharge(val id: Long, val date: String, val amount: Decimal, val concept: String,
    val status: String, val username: String, val applied: Decimal, val balance: Decimal)
@Serializable data class CreditMovement(val date: String, val type: String, val reference: String, val detail: String? = null,
    val charge: Decimal, val payment: Decimal, val resultingBalance: Decimal, val username: String,
    val reversed: Boolean, val entityId: Long)
@Serializable data class CreditStatement(val customer: CreditCustomer, val credits: List<CreditSale>,
    val payments: List<CreditPayment>, val movements: List<CreditMovement>, val charges: List<CreditCharge> = emptyList())
@Serializable data class CreditPaymentRequest(val amount: Decimal, val method: String, val notes: String? = null)
@Serializable data class CreditPaymentResult(val id: Long, val amount: Decimal, val method: String,
    val customerBalance: Decimal, val createdAt: String)
@Serializable data class CreditReason(val reason: String)
@Serializable data class CreditPaymentAttempt(val key: String, val customerId: Long, val request: CreditPaymentRequest)
