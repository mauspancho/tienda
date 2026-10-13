package com.mauspancho.tiendapos

import androidx.test.platform.app.InstrumentationRegistry
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.data.storage.SessionVault
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.KeyStore

class SessionVaultDeviceTest {
    @Test fun keystoreEncryptionSurvivesNewInstanceAndRejectsTampering() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alias = "tiendapos.test.session.v1"
        val fileName = "test-session.enc"
        val file = File(context.noBackupFilesDir, fileName)
        val session = StoredSession("https://fixture.example/", Session("fixture-access-sensitive", "fixture-refresh-sensitive", "Bearer", 900,
            User(1, "fixture", "Prueba", enabled = true, roles = listOf("ADMIN"))))
        val vault = SessionVault(context, Json, fileName, alias)
        try {
            vault.write(session)
            val ciphertext = file.readBytes()
            assertFalse(ciphertext.toString(Charsets.ISO_8859_1).contains("fixture-access-sensitive"))
            assertFalse(ciphertext.toString(Charsets.ISO_8859_1).contains("fixture-refresh-sensitive"))
            assertEquals(session, SessionVault(context, Json, fileName, alias).read())
            ciphertext[ciphertext.lastIndex] = (ciphertext.last().toInt() xor 1).toByte()
            file.writeBytes(ciphertext)
            assertNull(vault.read()); assertFalse(file.exists())
        } finally { vault.clear(); KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) } }
    }
}
