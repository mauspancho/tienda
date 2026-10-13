package com.mauspancho.tiendapos.ui

import androidx.lifecycle.*
import com.mauspancho.tiendapos.TiendaApplication
import com.mauspancho.tiendapos.data.api.*
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.data.repository.RemoteAuthRepository
import com.mauspancho.tiendapos.data.storage.Preferences
import com.mauspancho.tiendapos.ui.components.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

enum class StartScreen { RESTORING, SERVER, LOGIN, HOME }
class AppViewModel(private val app: TiendaApplication, private val saved: SavedStateHandle) : TaskViewModel(app.json) {
    val screen = MutableStateFlow(StartScreen.RESTORING)
    val preferences = app.preferences.values.stateIn(viewModelScope, SharingStarted.Eagerly, Preferences())
    val user = MutableStateFlow<User?>(null)
    val notice = MutableStateFlow<String?>(null)
    val scopeId = MutableStateFlow(saved.get<String>("scope") ?: UUID.randomUUID().toString().also { saved["scope"] = it })
    var client: ApiClient? = null
        private set
    private var watcher: Job? = null
    private var restoring: Job? = null
    val changing = MutableStateFlow(false)
    init { restore() }
    fun restore() {
        if (restoring?.isActive == true) return
        restoring = task(operation) {
            screen.value = StartScreen.RESTORING
            val prefs = app.preferences.values.first()
            val server = prefs.server
            if (server == null) { screen.value = StartScreen.SERVER; return@task }
            if (client == null) client = app.client(server)
            val remote = client!!
            val found = try { RemoteAuthRepository(remote).restore() } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (remote.sessions.state.value == null) { notice.value = "Tu sesión ha expirado."; screen.value = StartScreen.LOGIN; return@task }
                throw e
            }
            if (found != null) signedIn(found) else screen.value = StartScreen.LOGIN
        }
    }
    fun signedIn(value: User) {
        user.value = value; screen.value = StartScreen.HOME; notice.value = null
        watcher?.cancel()
        val watching = client ?: return
        watcher = viewModelScope.launch {
            watching.sessions.state.collect { session ->
                if (session == null && screen.value == StartScreen.HOME && client === watching) {
                    watching.cancel()
                    withContext(Dispatchers.IO) { watching.sessions.close() }
                    client = preferences.value.server?.let(app::client)
                    user.value = null; rotateScope(); notice.value = "Tu sesión ha expirado."; screen.value = StartScreen.LOGIN
                } else if (session != null && client === watching) user.value = session.user
            }
        }
    }
    private fun rotateScope() { scopeId.value = UUID.randomUUID().toString(); saved["scope"] = scopeId.value }
    private suspend fun closeLocal() {
        watcher?.cancel(); watcher = null
        val previous = client
        withContext(Dispatchers.IO) { previous?.sessions?.close(); app.vault.clear() }
        previous?.cancel(); client = null; user.value = null; rotateScope()
        app.media.clear()
    }
    suspend fun saveServer(server: String, health: Health) {
        closeLocal()
        app.preferences.server(server, health.serverVersion, health.apiVersion)
        client = app.client(server); changing.value = false; screen.value = StartScreen.LOGIN
    }
    fun changeServer() {
        task(operation) {
            val previous = client
            val token = previous?.sessions?.state?.value?.refreshToken
            closeLocal(); changing.value = true; screen.value = StartScreen.SERVER
            if (token != null) try { previous.auth.logout(RefreshRequest(token)) }
            catch (e: CancellationException) { throw e } catch (_: Exception) { /* Credentials remain cleared locally. */ }
        }
    }
    fun logout() {
        if (operation.value.loading) return
        task(operation) {
            val remote = client
            val token = remote?.sessions?.state?.value?.refreshToken
            val server = preferences.value.server
            closeLocal()
            if (server != null) client = app.client(server)
            screen.value = StartScreen.LOGIN
            if (token != null) try { remote.auth.logout(RefreshRequest(token)) }
            catch (e: CancellationException) { throw e } catch (_: Exception) { notice.value = "Sesión cerrada en este dispositivo. No fue posible revocar la sesión en el servidor." }
        }
    }
    fun theme(value: String) { viewModelScope.launch { app.preferences.theme(value) } }
    override fun onCleared() { client?.cancel() }
}
