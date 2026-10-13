package com.mauspancho.tiendapos.domain.repository

import com.mauspancho.tiendapos.data.dto.*

interface CreditRepository {
    suspend fun available(): Boolean
    suspend fun summary(): CreditSummary
    suspend fun customers(q: String, filter: String, page: Int): Page<CreditCustomer>
    suspend fun customerSearch(q: String, page: Int): Page<Customer>
    suspend fun customer(id: Long): Customer
    suspend fun saveCustomer(id: Long?, request: CustomerRequest): Customer
    suspend fun statement(id: Long): CreditStatement
    suspend fun payment(attempt: CreditPaymentAttempt): CreditPaymentResult
    suspend fun reverse(payment: Boolean, id: Long, reason: String)
}
