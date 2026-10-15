package com.mauspancho.tiendapos.domain.repository

import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.domain.model.*
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val session: Flow<Session?>
    suspend fun login(username: String, password: String): User
    suspend fun restore(): User?
    suspend fun logout()
}
interface ProductRepository {
    fun pages(query: String, active: Boolean? = null, brand: String? = null, whatsapp: Boolean? = null): Flow<PagingData<Product>>
    suspend fun findBarcode(code: String): Product
    suspend fun search(query: String): List<Product>
}
interface PosRepository {
    suspend fun state(): PosState
    suspend fun checkout(attempt: CheckoutAttempt): Sale
}
fun interface ServerRepository { suspend fun check(server: String): Health }
