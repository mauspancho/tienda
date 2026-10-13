package com.mauspancho.tiendapos.data.api

import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.domain.model.ServerUrl
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient(val baseUrl: String, val sessions: SessionManager, val json: Json, lanHost: String) {
    val transport = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .addInterceptor { chain -> ServerUrl.checkTransport(chain.request().url, lanHost); chain.proceed(chain.request()) }.build()
    private fun retrofit(client: OkHttpClient) = Retrofit.Builder().baseUrl(baseUrl).client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
    val auth = retrofit(transport).create(AuthApi::class.java)
    private val authenticated = transport.newBuilder().addInterceptor { chain ->
        val origin = baseUrl.toHttpUrl()
        val target = chain.request().url
        require(target.scheme == origin.scheme && target.host == origin.host && target.port == origin.port &&
            target.encodedPath.startsWith(origin.encodedPath + "api/v1/")) { "La petición no pertenece a la API del servidor configurado." }
        val session = sessions.state.value
        val owner = chain.request().tag(SessionOwner::class.java)
        require(owner == null || (session != null && owner.matches(session))) { "La petición pertenece a otra sesión." }
        val request = chain.request().newBuilder().apply {
            if (session != null) {
                header("Authorization", "Bearer ${session.accessToken}")
                tag(SessionOwner::class.java, owner ?: SessionOwner(session.user.id, session.user.username))
            }
        }.build()
        chain.proceed(request)
    }.authenticator(SessionAuthenticator(sessions, auth)).build()
    val api = retrofit(authenticated).create(TiendaApi::class.java)
    fun cancel() { authenticated.dispatcher.cancelAll(); transport.dispatcher.cancelAll() }
}
