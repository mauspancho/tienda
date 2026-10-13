package com.mauspancho.tiendapos.domain.model

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ServerUrl {
    fun normalize(input: String): String {
        val value = input.trim()
        require(value.isNotEmpty()) { "Ingresa la URL del servidor." }
        require(value.none { it.isWhitespace() } && !value.contains('\\')) { "La URL no puede contener espacios ni barras invertidas." }
        val url = value.toHttpUrlOrNull() ?: throw IllegalArgumentException("Ingresa una URL http o https válida.")
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
            "La URL no puede incluir credenciales, parámetros ni fragmentos."
        }
        val path = url.encodedPath.split('/').filter { it.isNotEmpty() }
        require(path.none { it == "api" || it == "v1" }) { "Ingresa la URL raíz del servidor, sin /api/v1." }
        require(url.isHttps || privateIpv4(url.host)) { "Usa HTTPS para Internet. HTTP solo admite una IPv4 privada de LAN." }
        return url.newBuilder().encodedPath(if (path.isEmpty()) "/" else "/${path.joinToString("/")}/").build().toString()
    }
    fun privateIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4 || parts.any { it.isEmpty() || it.any { c -> !c.isDigit() } || (it.length > 1 && it[0] == '0') }) return false
        val values = parts.map { it.toIntOrNull() ?: return false }
        return values.all { it in 0..255 } && (values[0] == 10 ||
            (values[0] == 192 && values[1] == 168) || (values[0] == 172 && values[1] in 16..31))
    }
    fun checkTransport(url: HttpUrl, allowedLanHost: String) {
        require(url.isHttps || (privateIpv4(url.host) && url.host == allowedLanHost)) {
            "Esta compilación admite HTTPS. Para HTTP local compila con -PlanHost=${url.host}."
        }
    }
    fun image(base: String, path: String?): String? {
        if (path.isNullOrBlank()) return null
        val origin = base.toHttpUrlOrNull() ?: return null
        val result = origin.resolve(path) ?: return null
        if (origin.isHttps && !result.isHttps) return null
        if (!result.isHttps && (!privateIpv4(result.host) || result.host != origin.host)) return null
        if (result.username.isNotEmpty() || result.password.isNotEmpty()) return null
        return result.toString()
    }
}
