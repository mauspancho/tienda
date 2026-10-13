package com.mauspancho.tiendapos.data.repository

import com.mauspancho.tiendapos.data.api.TiendaApi
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.domain.repository.CreditRepository
import retrofit2.HttpException

class RemoteCreditRepository(private val api: TiendaApi) : CreditRepository {
    override suspend fun available() = "CREDIT" in api.pos().settlementTypes
    override suspend fun summary() = api.creditSummary()
    override suspend fun customers(q: String, filter: String, page: Int) = api.creditCustomers(q, filter, page)
    override suspend fun customerSearch(q: String, page: Int) = api.customers(q, page)
    override suspend fun customer(id: Long) = api.customer(id)
    override suspend fun saveCustomer(id: Long?, request: CustomerRequest) =
        if (id == null) api.createCustomer(request) else api.updateCustomer(id, request)
    override suspend fun statement(id: Long) = api.creditStatement(id)
    override suspend fun payment(attempt: CreditPaymentAttempt): CreditPaymentResult {
        require(available()) { "El servidor necesita la actualización de Créditos." }
        return api.creditPayment(attempt.customerId, attempt.key, attempt.request)
    }
    override suspend fun reverse(payment: Boolean, id: Long, reason: String) {
        require(available()) { "El servidor necesita la actualización de Créditos." }
        val response = if (payment) api.reverseCreditPayment(id, CreditReason(reason)) else api.cancelCredit(id, CreditReason(reason))
        if (!response.isSuccessful) throw HttpException(response)
    }
}
