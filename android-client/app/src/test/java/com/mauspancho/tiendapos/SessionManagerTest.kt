package com.mauspancho.tiendapos

import com.mauspancho.tiendapos.data.api.SessionManager
import com.mauspancho.tiendapos.data.api.SessionOwner
import com.mauspancho.tiendapos.data.dto.StoredSession
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class SessionManagerTest {
    @Test fun previousAccountRequestCannotUseAnotherAccountsNewToken() {
        val manager = SessionManager("https://a/", MemoryStore()).apply { save(session()) }
        val owner = SessionOwner(1, "fixture")
        val other = session("store-b").let { it.copy(user = it.user.copy(id = 2, username = "store-b-user")) }
        manager.save(other)
        var refreshes = 0
        assertNull(manager.renew("access", owner) { refreshes++; other })
        assertEquals(0, refreshes); assertEquals("store-b", manager.state.value?.accessToken)
    }
    @Test fun refreshReturningAnotherIdentityIsRejectedAndCleared() {
        val manager = SessionManager("https://a/", MemoryStore()).apply { save(session()) }
        val other = session("foreign").let { it.copy(user = it.user.copy(id = 2)) }
        assertNull(manager.renew("access", SessionOwner(1, "fixture")) { other })
        assertNull(manager.state.value)
    }
    @Test fun savesAndRestoresServerBoundSession() {
        val store = MemoryStore()
        SessionManager("https://a/", store).save(session())
        val manager = SessionManager("https://a/", store); manager.restore()
        assertEquals("access", manager.state.value?.accessToken)
    }
    @Test fun neverReusesTokensOnAnotherServer() {
        val store = MemoryStore().apply { value = StoredSession("https://a/", session()) }
        val manager = SessionManager("https://b/", store); manager.restore()
        assertNull(manager.state.value); assertNull(store.value)
    }
    @Test fun concurrentUnauthorizedRequestsRefreshOnce() {
        val manager = SessionManager("https://a/", MemoryStore()).apply { save(session()) }
        val calls = AtomicInteger()
        val pool = Executors.newFixedThreadPool(8)
        try {
            val tokens = (1..24).map { pool.submit<String?> { manager.renew("access") { calls.incrementAndGet(); Thread.sleep(20); session("next", "rotated") } } }
            assertTrue(tokens.all { it.get(3, TimeUnit.SECONDS) == "next" }); assertEquals(1, calls.get())
        } finally { pool.shutdownNow() }
    }
    @Test fun logoutPreventsLateRefreshResurrectingCredentials() {
        val store = MemoryStore(); val manager = SessionManager("https://a/", store).apply { save(session()) }
        manager.close()
        assertNull(manager.renew("access") { session("late") })
        assertTrue(runCatching { manager.save(session("late")) }.isFailure)
        assertNull(manager.state.value); assertNull(store.value)
    }
    @Test fun invalidRefreshClearsCredentials() {
        val store = MemoryStore(); val manager = SessionManager("https://a/", store).apply { save(session()) }
        assertNull(manager.renew("access") { manager.clear(); null }); assertNull(store.value)
    }
    @Test fun transientRefreshFailureKeepsCredentials() {
        val manager = SessionManager("https://a/", MemoryStore()).apply { save(session()) }
        assertNull(manager.renew("access") { null }); assertEquals("refresh", manager.state.value?.refreshToken)
    }
    @Test fun lateRejectionOfOldAccessTokenDoesNotEraseNewSession() {
        val manager = SessionManager("https://a/", MemoryStore()).apply { save(session("new")) }
        manager.reject("old"); assertEquals("new", manager.state.value?.accessToken)
        manager.reject("new"); assertNull(manager.state.value)
    }
}
