package com.mauspancho.tiendapos

import com.mauspancho.tiendapos.domain.model.ServerUrl
import com.mauspancho.tiendapos.data.dto.Health
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class ServerUrlTest {
    @Test fun normalizesRootsAndContextWithoutDuplicatingSlashes() {
        assertEquals("https://tienda.example/", ServerUrl.normalize(" https://tienda.example// "))
        assertEquals("https://tienda.example/pos/", ServerUrl.normalize("https://tienda.example//pos///"))
        assertEquals("http://192.168.1.146:8092/", ServerUrl.normalize("http://192.168.1.146:8092"))
    }
    @Test fun rejectsInvalidAndDangerousUrls() {
        listOf("", "tienda.example", "ftp://example.com", "https://user:pass@example.com", "https://example.com?a=1", "https://example.com/#foo",
            "https://example.com/some path", "http://example.com", "http://8.8.8.8", "https://example.com/api/v1").forEach {
            assertTrue("Debe rechazar $it", runCatching { ServerUrl.normalize(it) }.isFailure)
        }
    }
    @Test fun lanNeedsExplicitBuildAllowlist() {
        assertTrue(runCatching { ServerUrl.checkTransport("http://192.168.1.8/".toHttpUrl(), "") }.isFailure)
        ServerUrl.checkTransport("http://192.168.1.8/".toHttpUrl(), "192.168.1.8")
        assertTrue(runCatching { ServerUrl.checkTransport("http://192.168.1.9/".toHttpUrl(), "192.168.1.8") }.isFailure)
    }
    @Test fun healthRequiresExactApplicationAndVersion() {
        Health("UP", "Tienda POS", "v1", "0.1.0").validate()
        listOf(Health("UP", "Otro POS", "v1", "1"), Health("DOWN", "Tienda POS", "v1", "1"), Health("UP", "Tienda POS", "v2", "1"))
            .forEach { assertTrue(runCatching { it.validate() }.isFailure) }
    }
    @Test fun imageResolutionAvoidsInsecureDowngrade() {
        assertEquals("https://example.com/uploads/p.jpg", ServerUrl.image("https://example.com/pos/", "/uploads/p.jpg"))
        assertEquals("https://cdn.example/p.jpg", ServerUrl.image("https://example.com/", "https://cdn.example/p.jpg"))
        assertNull(ServerUrl.image("https://example.com/", "http://192.168.1.8/p.jpg"))
        assertNull(ServerUrl.image("https://example.com/", "javascript:alert(1)"))
    }
}
