package com.mauspancho.tiendapos.data.api

import com.mauspancho.tiendapos.data.dto.ApiError
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.net.*
import java.io.IOException
import javax.net.ssl.SSLException

data class Failure(val message: String, val status: Int? = null, val uncertain: Boolean = false, val fields: Map<String, String> = emptyMap())
fun Throwable.failure(json: Json): Failure {
    if (this is CancellationException) throw this
    return when (this) {
        is HttpException -> {
            val error = runCatching { json.decodeFromString<ApiError>(response()?.errorBody()?.string().orEmpty()) }.getOrNull()
            val fallback = when (code()) {
                401 -> "Tu sesión ha expirado."
                403 -> "No tienes permisos para realizar esta acción."
                404 -> "No se encontró el recurso solicitado."
                409 -> "La operación entra en conflicto con el estado actual."
                400, 422 -> "Revisa los datos del formulario."
                else -> "El servidor no pudo completar la operación."
            }
            Failure(if (code() == 403 || code() == 401 || code() >= 500) fallback else safeMessage(error?.message) ?: fallback,
                code(), code() >= 500, error?.fieldErrors.orEmpty().mapValues { safeMessage(it.value) ?: "Revisa este campo." })
        }
        is SocketTimeoutException -> Failure("El servidor tardó demasiado. Reintenta cuando tengas conexión.", uncertain = true)
        is UnknownHostException -> Failure("No se pudo resolver el nombre del servidor (DNS).", uncertain = true)
        is SSLException -> Failure("No se pudo verificar la conexión HTTPS. Revisa el certificado del servidor.")
        is ConnectException -> Failure("Servidor no encontrado o sin conexión.", uncertain = true)
        is SerializationException -> Failure("El servidor devolvió una respuesta inválida o incompatible.", uncertain = true)
        is IOException -> Failure("Sin conexión. Comprueba tu red y vuelve a intentar.", uncertain = true)
        is IllegalArgumentException -> Failure(message ?: "Revisa los datos ingresados.")
        else -> Failure("No se pudo completar la operación. Intenta de nuevo.", uncertain = true)
    }
}
private fun safeMessage(value: String?): String? = value?.takeIf {
    it.length <= 300 && !it.contains("Exception") && !it.contains("java.") && !it.contains("<html", true) && !it.trim().startsWith('{')
}
