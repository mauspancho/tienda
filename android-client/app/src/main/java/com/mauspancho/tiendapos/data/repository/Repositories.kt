package com.mauspancho.tiendapos.data.repository

import androidx.paging.*
import com.mauspancho.tiendapos.data.api.*
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.domain.model.*
import com.mauspancho.tiendapos.domain.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RemoteAuthRepository(private val client: ApiClient) : AuthRepository {
    override val session = client.sessions.state
    override suspend fun login(username: String, password: String): User {
        require(username.isNotBlank() && password.isNotEmpty()) { "Ingresa usuario y contraseña." }
        val result = client.auth.login(LoginRequest(username.trim(), password))
        withContext(Dispatchers.IO) { client.sessions.save(result) }
        val user = client.api.me()
        withContext(Dispatchers.IO) { client.sessions.save(result.copy(user = user)) }
        return user
    }
    override suspend fun restore(): User? {
        withContext(Dispatchers.IO) { client.sessions.restore() }
        val previous = client.sessions.state.value ?: return null
        val user = client.api.me()
        val latest = client.sessions.state.value ?: return null
        withContext(Dispatchers.IO) { client.sessions.save(latest.copy(user = user)) }
        return user.takeIf { previous.user.id == it.id }
    }
    override suspend fun logout() {
        val token = client.sessions.state.value?.refreshToken
        withContext(Dispatchers.IO) { client.sessions.close() }
        client.cancel()
        if (token != null) try { client.auth.logout(RefreshRequest(token)) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { /* Local logout remains effective offline. */ }
    }
}
class RemoteProductRepository(private val api: TiendaApi) : ProductRepository {
    override fun pages(query: String, active: Boolean?, brand: String?) = Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
        ApiPagingSource { page -> api.products(query, page, active = active, brand = brand) }
    }.flow
    override suspend fun findBarcode(code: String) = api.barcode(code)
    override suspend fun search(query: String) = api.products(query, 0, active = true).content
}
class ApiPagingSource<T : Any>(private val fetch: suspend (Int) -> Page<T>) : PagingSource<Int, T>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> = try {
        val page = params.key ?: 0
        val result = fetch(page)
        LoadResult.Page(result.content, if (page == 0) null else page - 1, if (page + 1 >= result.totalPages) null else page + 1)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { LoadResult.Error(e) }
    override fun getRefreshKey(state: PagingState<Int, T>): Int? = state.anchorPosition?.let {
        state.closestPageToPosition(it)?.let { page -> page.prevKey?.plus(1) ?: page.nextKey?.minus(1) }
    }
}
class RemotePosRepository(private val api: TiendaApi) : PosRepository {
    override suspend fun state() = api.pos()
    override suspend fun checkout(attempt: CheckoutAttempt) = api.checkout(attempt.key, attempt.request)
}
class RemoteServerRepository(private val createClient: (String) -> ApiClient) : ServerRepository {
    override suspend fun check(server: String): Health {
        val client = createClient(server)
        return try { client.auth.health().validate() } finally { client.cancel() }
    }
}
