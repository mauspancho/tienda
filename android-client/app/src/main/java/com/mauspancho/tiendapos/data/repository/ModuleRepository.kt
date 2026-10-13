package com.mauspancho.tiendapos.data.repository

import androidx.paging.*
import com.mauspancho.tiendapos.data.api.ApiClient
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.domain.model.FormSpec
import com.mauspancho.tiendapos.navigation.Destination
import kotlinx.serialization.json.*

class ModuleRepository(private val api: com.mauspancho.tiendapos.data.api.TiendaApi, val json: Json) {
    constructor(client: ApiClient) : this(client.api, client.json)
    fun pages(path: String, query: Map<String, String>) = Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
        ApiPagingSource { page -> json.decodeFromJsonElement<Page<JsonObject>>(api.read("api/v1/$path", query + mapOf("page" to "$page", "size" to "20"))) }
    }.flow
    suspend fun read(path: String, query: Map<String, String> = emptyMap()) = api.read("api/v1/$path", query)
    suspend fun detail(path: String, value: JsonObject): JsonObject {
        if (path == "finances/capital") return value // List response already contains the full record; no detail endpoint exists.
        val identifier = if (path == "sales") value["folio"]?.jsonPrimitive?.content else value["id"]?.jsonPrimitive?.content
        require(identifier != null) { "No se encontró el registro." }
        val encoded = okhttp3.HttpUrl.Builder().scheme("https").host("placeholder.invalid").addPathSegment(identifier).build().encodedPath.removePrefix("/")
        return read("${if (path == "inventory/stock") "products" else path}/$encoded").jsonObject
    }
    suspend fun submit(spec: FormSpec, payload: JsonObject, key: String?): JsonObject {
        val body = if (spec.edit && spec.path.startsWith("products/")) {
            val latest = read(spec.path).jsonObject
            JsonObject(payload + ("currentStock" to latest.getValue("currentStock")))
        } else payload
        val result = if (spec.edit) api.update("api/v1/${spec.path}", body) else api.create("api/v1/${spec.path}", body, key)
        return result.jsonObject
    }
    suspend fun active(path: String, id: Long, enabled: Boolean) = api.patch("api/v1/$path/$id/active",
        buildJsonObject { put("active", enabled) }).jsonObject
    suspend fun reverse(id: Long) = api.create("api/v1/inventory/movements/$id/reverse", buildJsonObject { }).jsonObject
    suspend fun deleteUser(id: Long) { val response = api.delete("api/v1/users/$id"); if (!response.isSuccessful) throw retrofit2.HttpException(response) }
    suspend fun references(path: String, q: String, page: Int): Page<JsonObject> {
        val result = read(path, if (path == "expenses/categories") emptyMap() else mapOf("q" to q, "page" to "$page", "size" to "20"))
        return if (result is JsonArray) Page(result.map { it.jsonObject }, 0, result.size, result.size.toLong(), 1)
            else json.decodeFromJsonElement(result)
    }
}
