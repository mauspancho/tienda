package com.mauspancho.tiendapos

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.mauspancho.tiendapos.data.api.*
import com.mauspancho.tiendapos.data.storage.*
import com.mauspancho.tiendapos.domain.model.ServerUrl
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class TiendaApplication : Application(), SingletonImageLoader.Factory {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = true; encodeDefaults = true }
    val preferences by lazy { PreferencesStore(this) }
    val vault by lazy { SessionVault(this, json) }
    val media by lazy { MediaStorage(this) }
    fun client(server: String): ApiClient = ApiClient(server, SessionManager(server, vault), json, BuildConfig.LAN_HOST)
    // Health probes must never read/write the active session vault.
    fun probe(server: String): ApiClient = ApiClient(server, SessionManager(server, object : SessionStore {
        override fun read() = null
        override fun write(value: com.mauspancho.tiendapos.data.dto.StoredSession) = Unit
        override fun clear() = Unit
    }), json, BuildConfig.LAN_HOST)
    override fun newImageLoader(context: android.content.Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = {
            OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .addInterceptor { chain -> ServerUrl.checkTransport(chain.request().url, BuildConfig.LAN_HOST); chain.proceed(chain.request()) }.build()
        })) }.build()
}
