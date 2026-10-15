package com.mauspancho.tiendapos

import androidx.lifecycle.SavedStateHandle
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.data.storage.PendingOperationStore
import com.mauspancho.tiendapos.domain.repository.WhatsappRepository
import com.mauspancho.tiendapos.navigation.Destination
import com.mauspancho.tiendapos.ui.whatsapp.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import java.net.SocketTimeoutException

fun whatsappFixture(running: Boolean = false) = WhatsappState(WhatsappConnection(true, "Proveedor", "AVAILABLE", "CONNECTED", "5215550000000"),
    WhatsappAnnouncements("LISTO", true, 1, 1, 1, true), listOf(WhatsappGroup(7, "Clientes", true, true)),
    WhatsappSync(if (running) "RUNNING" else "COMPLETED", running, 1))
open class FakeWhatsappRepository : WhatsappRepository {
    var data = whatsappFixture()
    var timeout = false
    var processing = false
    var syncCalls = 0
    val sent = mutableListOf<PromotionAttempt>()
    val selections = mutableListOf<List<Long>>()
    override suspend fun state() = data
    override suspend fun action(action: String) = data
    override suspend fun qr() = byteArrayOf(1, 2, 3)
    override suspend fun sync() = data.sync
    override suspend fun startSync(): WhatsappSync { syncCalls++; return WhatsappSync("RUNNING", true, 1) }
    override suspend fun groups(ids: List<Long>): WhatsappState { selections.add(ids); return data }
    override suspend fun announcements(enabled: Boolean) = data.copy(announcements = data.announcements.copy(enabled = enabled))
    override suspend fun preview(id: Long) = PromotionPreview(true, true, suggestedMessage = "Oferta", groupNames = listOf("Clientes"), groupCount = 1)
    override suspend fun send(attempt: PromotionAttempt): PromotionResult {
        sent.add(attempt); if (timeout) throw SocketTimeoutException()
        return PromotionResult(if (processing) "PROCESSING" else "COMPLETED", if (processing) 0 else 1, 0, sent.size > 1, processing,
            listOf(PromotionDelivery("Clientes", if (processing) "PENDING" else "ACCEPTED")))
    }
}
@OptIn(ExperimentalCoroutinesApi::class)
class WhatsappTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun start() { Dispatchers.setMain(dispatcher) }
    @After fun finish() { Dispatchers.resetMain() }
    @Test fun groupSyncDoesNotStartTwiceAndLoadsServerSelections() = runTest(dispatcher) {
        val repo = FakeWhatsappRepository(); val vm = WhatsappViewModel(Json, repo)
        vm.refresh(); advanceUntilIdle(); assertEquals(setOf(7L), vm.selectedGroups.value)
        vm.startSync(); vm.startSync(); advanceUntilIdle(); vm.startSync(); advanceUntilIdle(); assertEquals(1, repo.syncCalls)
        vm.pollSync(); advanceUntilIdle(); assertFalse(vm.sync.value.value!!.running)
        vm.select(7, false); vm.saveGroups(); advanceUntilIdle(); assertEquals(emptyList<Long>(), repo.selections.single())
        assertTrue(Destination.WHATSAPP.allowed(listOf("ADMIN"))); assertFalse(Destination.WHATSAPP.allowed(listOf("CAJERO")))
    }
    @Test fun timeoutKeepsImmutablePromotionAndSurvivesColdStart() = runTest(dispatcher) {
        val repo = FakeWhatsappRepository().apply { timeout = true }
        val store = object : PendingOperationStore {
            var raw: String? = null
            override suspend fun read() = raw
            override suspend fun write(value: String) { raw = value }
            override suspend fun clear() { raw = null }
        }
        val vm = PromotionViewModel(Json, repo, SavedStateHandle(), 5, "Arroz", store)
        advanceUntilIdle(); val request = vm.validate("Oferta 1", true, "+5215550000000")
        vm.send(request); vm.send(request); advanceUntilIdle(); assertEquals(1, repo.sent.size); assertNotNull(store.raw)
        val cold = PromotionViewModel(Json, repo, SavedStateHandle(), 99, "Otro", store)
        advanceUntilIdle(); assertEquals(5L, cold.productId.value); assertEquals("Arroz", cold.productName.value)
        cold.send(PromotionRequest("Oferta diferente")); advanceUntilIdle(); assertEquals(repo.sent[0], repo.sent[1])
        repo.timeout = false; cold.send(); advanceUntilIdle(); assertNull(store.raw); assertNull(cold.attempt.value)
        cold.send(request); advanceUntilIdle(); assertEquals(3, repo.sent.size)
    }
    @Test fun processingResponseKeepsKeyAndValidationRejectsBadInput() = runTest(dispatcher) {
        val repo = FakeWhatsappRepository().apply { processing = true }
        val vm = PromotionViewModel(Json, repo, SavedStateHandle(), 5, "Arroz")
        advanceUntilIdle()
        assertTrue(runCatching { vm.validate("", false, "") }.isFailure)
        assertTrue(runCatching { vm.validate("x".repeat(4001), false, "") }.isFailure)
        assertTrue(runCatching { vm.validate("Oferta", true, "abc") }.isFailure)
        vm.send(vm.validate("Oferta", false, "")); advanceUntilIdle(); assertNotNull(vm.attempt.value)
        vm.send(); advanceUntilIdle(); assertEquals(repo.sent[0], repo.sent[1])
    }
    @Test fun validationErrorOnRetryDoesNotDiscardAnUncertainSend() = runTest(dispatcher) {
        var calls = 0
        val repo = object : FakeWhatsappRepository() {
            override suspend fun send(attempt: PromotionAttempt): PromotionResult {
                if (calls++ == 0) throw SocketTimeoutException()
                throw retrofit2.HttpException(retrofit2.Response.error<PromotionResult>(422,
                    okhttp3.ResponseBody.create(null, "{\"message\":\"Producto sin existencia\",\"status\":422}")))
            }
        }
        val vm = PromotionViewModel(Json, repo, SavedStateHandle(), 5, "Arroz")
        advanceUntilIdle(); vm.send(vm.validate("Oferta", false, "")); advanceUntilIdle()
        val pending = vm.attempt.value
        vm.send(); advanceUntilIdle()
        assertNotNull(pending); assertEquals(pending, vm.attempt.value)
    }
}
