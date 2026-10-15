package com.mauspancho.tiendapos.data.repository

import com.mauspancho.tiendapos.data.api.TiendaApi
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.domain.repository.WhatsappRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class RemoteWhatsappRepository(private val api: TiendaApi) : WhatsappRepository {
    override suspend fun state() = api.whatsapp()
    override suspend fun action(action: String): WhatsappState {
        require(action in setOf("connect", "reconnect", "refresh", "disassociate"))
        return api.whatsappAction(action)
    }
    override suspend fun qr(): ByteArray = withContext(Dispatchers.IO) {
        api.whatsappQr().use { body ->
            require(body.contentType()?.subtype == "png") { "El servidor no devolvió un QR PNG válido." }
            body.byteStream().use { input ->
                val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val count = input.read(buffer); if (count < 0) break
                    require(output.size() + count <= 2 * 1024 * 1024) { "El QR es demasiado grande." }; output.write(buffer, 0, count) }
                output.toByteArray()
            }
        }
    }
    override suspend fun sync() = api.whatsappSync()
    override suspend fun startSync() = api.syncWhatsappGroups()
    override suspend fun groups(ids: List<Long>) = api.whatsappGroups(WhatsappSelection(ids))
    override suspend fun announcements(enabled: Boolean) = api.whatsappAnnouncements(Enabled(enabled))
    override suspend fun preview(id: Long) = api.promotionPreview(id)
    override suspend fun send(attempt: PromotionAttempt) = api.sendPromotion(attempt.productId, attempt.key, attempt.request)
}
