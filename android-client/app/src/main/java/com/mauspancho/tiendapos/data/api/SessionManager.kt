package com.mauspancho.tiendapos.data.api

import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.data.storage.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow

data class SessionOwner(val id: Long, val username: String) {
    fun matches(session: Session) = id == session.user.id && username == session.user.username
}

/** One manager per server; closing a manager prevents in-flight calls from resurrecting its credentials. */
class SessionManager(private val server: String, private val store: SessionStore) {
    val state = MutableStateFlow<Session?>(null)
    @Volatile private var closed = false
    @Synchronized fun restore() {
        if (closed) return
        val stored = store.read()
        if (stored?.server == server) state.value = stored.session else store.clear()
    }
    @Synchronized fun save(session: Session) {
        check(!closed) { "La sesión anterior ya está cerrada." }
        require(session.tokenType.equals("Bearer", true) && session.accessToken.isNotBlank() && session.refreshToken.isNotBlank() && session.user.enabled) {
            "Respuesta de autenticación inválida."
        }
        store.write(StoredSession(server, session)); state.value = session
    }
    @Synchronized fun clear() { store.clear(); state.value = null }
    @Synchronized fun reject(accessToken: String) { if (state.value?.accessToken == accessToken) clear() }
    @Synchronized fun close() { closed = true; clear() }
    @Synchronized fun renew(failedToken: String, owner: SessionOwner? = null, refresh: (String) -> Session?): String? {
        if (closed) return null
        val current = state.value ?: return null
        if (owner != null && !owner.matches(current)) return null
        if (current.accessToken != failedToken) return current.accessToken
        val next = refresh(current.refreshToken) ?: return null
        if (next.user.id != current.user.id || next.user.username != current.user.username) { clear(); return null }
        if (!closed) save(next)
        return state.value?.accessToken
    }
}
