package com.mauspancho.tiendapos

import androidx.test.platform.app.InstrumentationRegistry
import com.mauspancho.tiendapos.data.storage.EncryptedPendingOperationStore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class PendingStoreDeviceTest {
    @Test fun checkpointIsEncryptedScopedRecoverableAndTamperingFailsClosed() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = "checkpoint-test-${UUID.randomUUID()}"
        val first = EncryptedPendingOperationStore(context, scope)
        val other = EncryptedPendingOperationStore(context, "$scope-other-user")
        val previous = context.noBackupFilesDir.listFiles().orEmpty().map { it.name }.toSet()
        val checkpoint = "{\"key\":\"immutable-operation-key\",\"customerId\":7,\"amount\":12.30}"
        try {
            first.write(checkpoint)
            val file = context.noBackupFilesDir.listFiles().orEmpty().single { it.name.startsWith("pending-") && it.name !in previous }
            assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("immutable-operation-key"))
            assertEquals(checkpoint, EncryptedPendingOperationStore(context, scope).read())
            assertNull(other.read())
            // AtomicFile may leave a backup if a process dies during replacement.
            val backup = java.io.File(file.path + ".bak")
            assertTrue(file.renameTo(backup))
            assertEquals(checkpoint, first.read())
            val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
            assertTrue(runCatching { first.read() }.isFailure)
            assertTrue(file.exists())
        } finally { first.clear(); other.clear() }
    }
}
