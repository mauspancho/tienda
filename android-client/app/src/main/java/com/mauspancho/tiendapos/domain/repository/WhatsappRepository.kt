package com.mauspancho.tiendapos.domain.repository

import com.mauspancho.tiendapos.data.dto.*

interface WhatsappRepository {
    suspend fun state(): WhatsappState
    suspend fun action(action: String): WhatsappState
    suspend fun qr(): ByteArray
    suspend fun sync(): WhatsappSync
    suspend fun startSync(): WhatsappSync
    suspend fun groups(ids: List<Long>): WhatsappState
    suspend fun announcements(enabled: Boolean): WhatsappState
    suspend fun preview(id: Long): PromotionPreview
    suspend fun send(attempt: PromotionAttempt): PromotionResult
}
