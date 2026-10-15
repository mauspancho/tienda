package com.mauspancho.tiendapos.ui.auth

import com.mauspancho.tiendapos.data.dto.User
import com.mauspancho.tiendapos.domain.repository.AuthRepository
import com.mauspancho.tiendapos.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import com.mauspancho.tiendapos.data.dto.ApiError
import retrofit2.HttpException

class AuthViewModel(json: Json, private val repository: AuthRepository, private val signedIn: (User) -> Unit) : TaskViewModel(json) {
    val state = MutableStateFlow(TaskState<User>())
    fun login(username: String, password: String) {
        if (state.value.loading) return
        task(state) {
            val user = try { repository.login(username, password) }
            catch (e: HttpException) {
                if (e.code() != 401) throw e
                val error = runCatching { json.decodeFromString<ApiError>(e.response()?.errorBody()?.string().orEmpty()) }.getOrNull()
                throw IllegalArgumentException(if (error?.message == "Cuenta no disponible.")
                    "Esta cuenta no tiene acceso a la aplicación de tienda. Usa ADMIN o CAJERO de una tienda activa; PLATFORM_ADMIN se administra en la web."
                    else "No se pudo iniciar sesión. Revisa el usuario, la contraseña y el acceso a una tienda activa.")
            }
            user.also(signedIn)
        }
    }
}
