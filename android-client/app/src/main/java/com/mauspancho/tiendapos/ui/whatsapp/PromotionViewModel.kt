package com.mauspancho.tiendapos.ui.whatsapp

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mauspancho.tiendapos.data.api.*
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.data.storage.PendingOperationStore
import com.mauspancho.tiendapos.domain.repository.WhatsappRepository
import com.mauspancho.tiendapos.ui.components.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import java.util.UUID

class PromotionViewModel(json: Json, private val repository: WhatsappRepository, private val saved: SavedStateHandle,
    initialProductId: Long, initialProductName: String, private val durable: PendingOperationStore? = null) : TaskViewModel(json) {
    val productId = MutableStateFlow(initialProductId)
    val productName = MutableStateFlow(initialProductName)
    val preview = MutableStateFlow(TaskState<PromotionPreview>())
    val result = MutableStateFlow(TaskState<PromotionResult>())
    val attempt = MutableStateFlow(saved.get<String>("promotion")?.let { json.decodeFromString<PromotionAttempt>(it) })
    val restoring = MutableStateFlow(durable != null)
    init {
        if (durable == null) { restoreAttempt(); refresh() } else viewModelScope.launch {
            try {
                val raw = durable.read() ?: saved.get<String>("promotion")
                if (raw != null) { durable.write(raw); attempt.value = json.decodeFromString(raw); saved["promotion"] = raw }
                restoreAttempt(); restoring.value = false; refresh()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { operation.value = TaskState(error = Failure("No se pudo recuperar la promoción pendiente. No se enviará otra promoción.")) }
        }
    }
    private fun restoreAttempt() { attempt.value?.let { productId.value = it.productId; productName.value = it.productName } }
    fun refresh() { if (!preview.value.loading && !restoring.value) task(preview) { repository.preview(productId.value) } }
    fun validate(message: String, individual: Boolean, phone: String): PromotionRequest {
        require(message.trim().isNotEmpty() && message.trim().length <= 4000) { "El mensaje debe tener entre 1 y 4000 caracteres." }
        val value = preview.value.value ?: throw IllegalArgumentException("Carga la vista previa antes de enviar.")
        require(if (individual) value.individualReady else value.ready) { if (individual) value.individualWarning ?: "El número no está disponible." else value.warning ?: "Configura los grupos antes de enviar." }
        if (individual) require(phone.trim().matches(Regex("[+0-9 ()-]{8,30}"))) { "Ingresa el número con código de país." }
        return PromotionRequest(message.trim(), individual, if (individual) phone.trim() else null)
    }
    fun send(request: PromotionRequest? = null) {
        if (restoring.value || result.value.loading || result.value.value?.let { !it.processing } == true) return
        val retrying = attempt.value != null
        task(result) {
            val send = attempt.value ?: run {
                require(request != null) { "Revisa el mensaje y el destino." }
                val valid = validate(request.message, request.sendToNumber, request.phone.orEmpty())
                PromotionAttempt(UUID.randomUUID().toString(), productId.value, productName.value, valid).also {
                    attempt.value = it; saved["promotion"] = json.encodeToString(it)
                }
            }
            try {
                durable?.write(json.encodeToString(send))
                repository.send(send).also {
                    if (!it.processing) { durable?.clear(); attempt.value = null; saved.remove<String>("promotion") }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val fail = e.failure(json)
                if (!retrying && !fail.uncertain && fail.status in listOf(400, 422)) { durable?.clear(); attempt.value = null; saved.remove<String>("promotion") }
                throw e
            }
        }
    }
    fun newPromotion() { if (attempt.value == null && !result.value.loading) { result.value = TaskState(); refresh() } }
}
