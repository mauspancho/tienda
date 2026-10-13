package com.mauspancho.tiendapos.data.storage

import android.content.Context
import android.security.keystore.*
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

/** Only an immutable retry checkpoint, never a locally confirmed debt, balance or offline queue. */
interface PendingOperationStore {
    suspend fun read(): String?
    suspend fun write(value: String)
    suspend fun clear()
}

class EncryptedPendingOperationStore(context: Context, scope: String) : PendingOperationStore {
    private val scopeId = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray()).joinToString("") { "%02x".format(it) }
    private val alias = "tiendapos.pending.$scopeId"
    private val file = AtomicFile(File(context.noBackupFilesDir, "pending-$scopeId.enc"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        val base = file.baseFile
        if (!base.exists() && !File(base.path + ".bak").exists() && !File(base.path + ".new").exists()) return@withContext null
        val buffer = ByteBuffer.wrap(file.readFully())
        val size = buffer.get().toInt(); require(size == 12 && buffer.remaining() > size)
        val iv = ByteArray(size).also { buffer.get(it) }; val payload = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)); updateAAD(alias.toByteArray()) }
        // Corrupt checkpoints fail closed instead of silently allowing a duplicate write.
        cipher.doFinal(payload).toString(Charsets.UTF_8)
    }
    override suspend fun write(value: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(alias.toByteArray()) }
        val encrypted = cipher.doFinal(value.toByteArray())
        val bytes = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size).put(cipher.iv.size.toByte()).put(cipher.iv).put(encrypted).array()
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e }
    }
    override suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }
}
