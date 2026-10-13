package com.mauspancho.tiendapos.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mauspancho.tiendapos.data.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

data class TaskState<T>(val loading: Boolean = false, val value: T? = null, val error: Failure? = null)
abstract class TaskViewModel(protected val json: Json) : ViewModel() {
    val operation = MutableStateFlow(TaskState<Unit>())
    protected fun <T> task(state: MutableStateFlow<TaskState<T>>, block: suspend () -> T): Job {
        state.value = state.value.copy(loading = true, error = null)
        return viewModelScope.launch {
            try { state.value = TaskState(value = block()) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.value = state.value.copy(loading = false, error = e.failure(json)) }
        }
    }
    fun dismissError() { operation.value = TaskState() }
}
