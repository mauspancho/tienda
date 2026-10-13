package com.mauspancho.tiendapos

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.domain.repository.CreditRepository
import com.mauspancho.tiendapos.ui.credits.*
import kotlinx.serialization.json.Json
import org.junit.*
import java.math.BigDecimal

class CreditPermissionUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun cashierCanUseCreditAndPaymentsButCannotManageOrReverse() {
        val zero = BigDecimal.ZERO; val ten = BigDecimal.TEN
        val customer = CreditCustomer(7, "Ana", fullName = "Ana", active = true, balance = BigDecimal("20"), totalGranted = BigDecimal("30"), totalPaid = ten)
        val statement = CreditStatement(customer,
            listOf(CreditSale(1, "V1", "2026-10-08T10:00:00", ten, zero, ten, "COMPLETED", listOf(CreditSaleLine("Histórico", BigDecimal.ONE, ten, ten)))),
            listOf(CreditPayment(2, "2026-10-08T09:00:00", ten, "CASH", null, "ACTIVE", "fixture")),
            listOf(CreditMovement("2026-10-08T10:00:00", "CRÉDITO", "V1", null, ten, zero, ten, "fixture", false, 1),
                CreditMovement("2026-10-08T09:00:00", "ABONO", "AB-2", null, zero, ten, ten, "fixture", false, 2)),
            charges = listOf(CreditCharge(33, "2026-10-13T10:00:00", BigDecimal("20"), "Saldo inicial", "ACTIVE", "fixture", ten, ten)))
        val repository = object : CreditRepository {
            override suspend fun available() = true
            override suspend fun summary() = CreditSummary(ten, 1, ten, zero)
            override suspend fun customers(q: String, filter: String, page: Int) = Page(listOf(customer), 0, 20, 1, 1)
            override suspend fun customerSearch(q: String, page: Int) = Page(emptyList<Customer>(), 0, 20, 0, 0)
            override suspend fun customer(id: Long): Customer = error("Admin-only UI")
            override suspend fun saveCustomer(id: Long?, request: CustomerRequest): Customer = error("Admin-only UI")
            override suspend fun statement(id: Long) = statement
            override suspend fun payment(attempt: CreditPaymentAttempt): CreditPaymentResult = error("Unused")
            override suspend fun reverse(payment: Boolean, id: Long, reason: String) = error("Admin-only UI")
        }
        val vm = CreditsViewModel(Json, repository, SavedStateHandle())
        val picker = CustomerPickerViewModel(Json, repository, SavedStateHandle())
        compose.setContent { MaterialTheme { CreditsScreen(vm, picker, false) {} } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Ana").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Registrar cliente").assertDoesNotExist()
        compose.onNodeWithText("Ana").performClick()
        compose.onNodeWithTag("credits.list").performScrollToNode(hasText("1 cargos manuales pendientes"))
        compose.onNodeWithText("1 cargos manuales pendientes").assertExists()
        compose.onNodeWithTag("credits.list").performScrollToNode(hasText("Cargo manual · Saldo inicial"))
        compose.onNodeWithText("Saldo pendiente $10.00 MXN").assertExists()
        compose.onNodeWithTag("credits.list").performScrollToNode(hasText("Registrar abono"))
        compose.onNodeWithText("Registrar abono").assertIsEnabled()
        compose.onNodeWithText("Editar cliente").assertDoesNotExist()
        compose.onNodeWithTag("credits.list").performScrollToNode(hasText("Ver movimientos")); compose.onNodeWithText("Ver movimientos").performClick()
        compose.onNodeWithTag("credits.list").performScrollToNode(hasText("CRÉDITO · V1")); compose.onNodeWithText("Cancelar crédito").assertDoesNotExist()
        compose.onNodeWithTag("credits.list").performScrollToNode(hasText("ABONO · AB-2")); compose.onNodeWithText("Revertir abono").assertDoesNotExist()
    }
}
