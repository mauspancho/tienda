package com.mauspancho.tiendapos.data.dto

import kotlinx.serialization.Serializable

@Serializable data class WhatsappConnection(val providerConfigured: Boolean, val providerName: String? = null,
    val providerStatus: String? = null, val status: String, val phone: String? = null, val lastCheckedAt: String? = null, val error: String? = null)
@Serializable data class WhatsappAnnouncements(val status: String, val enabled: Boolean, val availableGroups: Long,
    val selectedGroups: Long, val selectedAvailableGroups: Long, val ready: Boolean)
@Serializable data class WhatsappGroup(val id: Long, val name: String, val available: Boolean, val selectedForAnnouncements: Boolean)
@Serializable data class WhatsappSync(val status: String, val running: Boolean, val groupCount: Int,
    val startedAt: String? = null, val completedAt: String? = null, val lastSuccessfulAt: String? = null, val error: String? = null)
@Serializable data class WhatsappState(val connection: WhatsappConnection, val announcements: WhatsappAnnouncements,
    val groups: List<WhatsappGroup>, val sync: WhatsappSync)
@Serializable data class WhatsappSelection(val groupIds: List<Long>)
@Serializable data class PromotionPreview(val ready: Boolean, val individualReady: Boolean, val warning: String? = null,
    val individualWarning: String? = null, val suggestedMessage: String, val groupNames: List<String>, val groupCount: Int)
@Serializable data class PromotionRequest(val message: String, val sendToNumber: Boolean = false, val phone: String? = null)
@Serializable data class PromotionDelivery(val groupName: String, val status: String, val error: String? = null)
@Serializable data class PromotionResult(val status: String, val accepted: Int, val failed: Int,
    val duplicate: Boolean, val processing: Boolean, val groups: List<PromotionDelivery>)
@Serializable data class PromotionAttempt(val key: String, val productId: Long, val productName: String, val request: PromotionRequest)
