package com.mauspancho.tiendapos.data.storage

import android.content.Context
import android.security.keystore.*
import android.util.AtomicFile
import com.mauspancho.tiendapos.data.dto.*
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

interface SessionStore {
    fun read(): StoredSession?
    fun write(value: StoredSession)
    fun clear()
}
/** AES/GCM authentication binds the encrypted file to this application; server binding is checked on read. */
class SessionVault(context: Context, private val json: Json, fileName: String = "session.enc",
    private val alias: String = "tiendapos.session.v1") : SessionStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    @Synchronized override fun read(): StoredSession? {
        if (!file.baseFile.exists()) return null
        return try {
            val buffer = ByteBuffer.wrap(file.readFully())
            val size = buffer.get().toInt()
            require(size == 12 && buffer.remaining() > size)
            val iv = ByteArray(size).also { buffer.get(it) }
            val payload = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)); updateAAD(alias.toByteArray())
            }
            json.decodeFromString<StoredSession>(cipher.doFinal(payload).toString(Charsets.UTF_8))
        } catch (_: Exception) { file.delete(); null }
    }
    @Synchronized override fun write(value: StoredSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key()); updateAAD(alias.toByteArray())
        }
        val payload = cipher.doFinal(json.encodeToString(value).toByteArray(Charsets.UTF_8))
        val bytes = ByteBuffer.allocate(1 + cipher.iv.size + payload.size).put(cipher.iv.size.toByte()).put(cipher.iv).put(payload).array()
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e }
    }
    @Synchronized override fun clear() { file.delete() }
}
