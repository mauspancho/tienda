package com.mauspancho.tiendapos.data.storage

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*

private val Context.preferences by preferencesDataStore("settings")
data class Preferences(val server: String? = null, val theme: String = "system", val backendVersion: String = "", val apiVersion: String = "")
class PreferencesStore(context: Context) {
    private val store = context.preferences
    private val url = stringPreferencesKey("server")
    private val theme = stringPreferencesKey("theme")
    private val backend = stringPreferencesKey("backend")
    private val api = stringPreferencesKey("api")
    val values = store.data.map { Preferences(it[url], it[theme] ?: "system", it[backend] ?: "", it[api] ?: "") }
    suspend fun server(value: String, serverVersion: String, apiVersion: String) {
        store.edit { it[url] = value; it[backend] = serverVersion; it[api] = apiVersion }
    }
    suspend fun theme(value: String) { store.edit { it[theme] = value } }
    suspend fun forgetServer() { store.edit { it.remove(url); it.remove(backend); it.remove(api) } }
}
