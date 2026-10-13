package com.mauspancho.tiendapos

import com.mauspancho.tiendapos.data.dto.Health
import com.mauspancho.tiendapos.domain.repository.ServerRepository
import com.mauspancho.tiendapos.ui.server.ServerViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ServerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun cannotSaveUntestedOrChangedUrl() = runTest(dispatcher) {
        var saved: String? = null
        val vm = ServerViewModel(Json, ServerRepository { Health("UP", "Tienda POS", "v1", "1") }) { url, _ -> saved = url }
        vm.edit("https://a.example"); vm.save(); advanceUntilIdle(); assertNull(saved)
        vm.test(); advanceUntilIdle(); assertNotNull(vm.connection.value.value)
        vm.edit("https://b.example"); assertNull(vm.connection.value.value); vm.save(); advanceUntilIdle(); assertNull(saved)
        vm.test(); advanceUntilIdle(); vm.save(); advanceUntilIdle(); assertEquals("https://b.example/", saved)
    }
    @Test fun cancellationPreventsOldProbeEnablingNewUrl() = runTest(dispatcher) {
        val vm = ServerViewModel(Json, ServerRepository { delay(1000); Health("UP", "Tienda POS", "v1", "1") }) { _, _ -> fail() }
        vm.edit("https://a.example"); vm.test(); runCurrent()
        vm.edit("https://b.example"); advanceUntilIdle(); assertNull(vm.connection.value.value)
    }
}
