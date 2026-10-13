package com.mauspancho.tiendapos.ui.auth

import com.mauspancho.tiendapos.data.dto.User
import com.mauspancho.tiendapos.domain.repository.AuthRepository
import com.mauspancho.tiendapos.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

class AuthViewModel(json: Json, private val repository: AuthRepository, private val signedIn: (User) -> Unit) : TaskViewModel(json) {
    val state = MutableStateFlow(TaskState<User>())
    fun login(username: String, password: String) {
        if (state.value.loading) return
        task(state) { repository.login(username, password).also(signedIn) }
    }
}
