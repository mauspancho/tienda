package com.mauspancho.tiendapos.ui.products

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.mauspancho.tiendapos.data.api.*
import com.mauspancho.tiendapos.data.dto.*
import com.mauspancho.tiendapos.domain.repository.ProductRepository
import com.mauspancho.tiendapos.ui.components.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ProductsViewModel(private val client: ApiClient, private val repository: ProductRepository,
    private val media: com.mauspancho.tiendapos.data.storage.MediaStorage) : TaskViewModel(client.json) {
    val query = MutableStateFlow("")
    val brand = MutableStateFlow("")
    val activeOnly = MutableStateFlow(false)
    val whatsappOnly = MutableStateFlow(false)
    private data class Filters(val query: String, val brand: String, val active: Boolean, val whatsapp: Boolean)
    val pages = combine(query.debounce(350), brand.debounce(350), activeOnly, whatsappOnly, ::Filters)
        .flatMapLatest { (q, b, active, whatsapp) -> repository.pages(q, if (active) true else null, b.ifBlank { null }, if (whatsapp) true else null) }.cachedIn(viewModelScope)
    val detail = MutableStateFlow(TaskState<Product>())
    fun open(id: Long) { task(detail) { client.api.product(id) } }
    fun image(product: Product, uri: android.net.Uri) {
        if (operation.value.loading) return
        task(operation) { detail.value = TaskState(value = media.upload(client, product, uri)) }
    }
    fun state(id: Long, action: String, enabled: Boolean) {
        if (operation.value.loading) return
        task(operation) { detail.value = TaskState(value = client.api.productState(id, action, Enabled(enabled))) }
    }
}
