package com.mauspancho.tiendapos.data.api

import com.mauspancho.tiendapos.data.dto.RefreshRequest
import okhttp3.*

class SessionAuthenticator(private val sessions: SessionManager, private val auth: AuthApi) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        var prior = response.priorResponse
        var count = 0
        while (prior != null) { count++; prior = prior.priorResponse }
        val failed = response.request.header("Authorization")?.removePrefix("Bearer ") ?: return null
        if (count >= 1) { sessions.reject(failed); return null }
        val owner = response.request.tag(SessionOwner::class.java)
        val token = sessions.renew(failed, owner) { refresh ->
            try {
                val result = auth.refresh(RefreshRequest(refresh)).execute()
                if (result.code() in listOf(400, 401, 403)) sessions.clear()
                result.body()?.takeIf { result.isSuccessful }
            } catch (_: java.io.IOException) { null }
        }
        return token?.let { response.request.newBuilder().header("Authorization", "Bearer $it").build() }
    }
}
