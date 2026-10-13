package com.mauspancho.tiendapos.ui.products

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.mauspancho.tiendapos.data.dto.Product
import com.mauspancho.tiendapos.domain.model.ServerUrl
import com.mauspancho.tiendapos.ui.components.*
import kotlinx.serialization.json.*

@Composable fun ProductImage(product: Product, base: String) {
    Surface(Modifier.size(64.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(product.name.take(1), style = MaterialTheme.typography.headlineMedium)
            AsyncImage(ServerUrl.image(base, product.imageUrl), contentDescription = "Imagen de ${product.name}", modifier = Modifier.fillMaxSize())
        }
    }
}
@Composable fun ProductsScreen(vm: ProductsViewModel, forms: ModuleViewModel, base: String, admin: Boolean, image: (Product) -> Unit) {
    val pages = vm.pages.collectAsLazyPagingItems()
    val query by vm.query.collectAsStateWithLifecycle()
    val brand by vm.brand.collectAsStateWithLifecycle()
    val active by vm.activeOnly.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val form by forms.form.collectAsStateWithLifecycle()
    val completed by forms.completed.collectAsStateWithLifecycle()
    var show by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(completed) { if (completed > 0) { pages.refresh(); detail.value?.let { vm.open(it.id) } } }
    LaunchedEffect(detail.value) { if (detail.value != null) pages.refresh() }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(query, { vm.query.value = it }, Modifier.fillMaxWidth().padding(16.dp), label = { Text("Nombre, marca, código o código de barras") }, singleLine = true)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(brand, { vm.brand.value = it }, Modifier.weight(1f), label = { Text("Marca") }, singleLine = true)
            FilterChip(active, { vm.activeOnly.value = !active }, label = { Text("Activos") })
        }
        if (admin) TextButton({ forms.create() }) { Text("Registrar producto") }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { PagingStatus(pages.loadState.refresh, forms.repository.json, pages::retry) }
            if (pages.itemCount == 0 && pages.loadState.refresh is androidx.paging.LoadState.NotLoading) item { Empty() }
            items(pages.itemCount, key = { index -> pages.peek(index)?.id ?: -index.toLong() - 1 }) { index -> pages[index]?.let { product ->
                Card(onClick = { show = true; vm.open(product.id) }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(product.name) }, supportingContent = { Text("${listOfNotNull(product.brand, product.presentation).joinToString(" · ")}\nStock ${product.currentStock} · ${product.category?.name.orEmpty()}" + if (product.lowStock) "\nStock bajo" else "") },
                        leadingContent = { ProductImage(product, base) }, trailingContent = { Text(product.salePrice.money()) })
                }
            } }
            item { PagingStatus(pages.loadState.append, forms.repository.json, pages::retry) }
            item { TextButton(pages::refresh) { Text("Actualizar") } }
        }
    }
    if (show) FullScreenDialog({ show = false }) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { TextButton({ show = false }) { Text("Volver") } }
            if (detail.loading) item { Loading() }
            item { ErrorCard(detail.error) }
            detail.value?.let { product ->
                item { ProductImage(product, base); NativeDetails(forms.repository.json.encodeToJsonElement(product)) }
                if (admin) {
                    item { Action("Editar") { forms.edit(forms.repository.json.encodeToJsonElement(product).jsonObject) } }
                    item { Action("Cambiar imagen", !operation.loading) { image(product) } }
                    item { Action(if (product.active) "Desactivar" else "Activar", !operation.loading) { vm.state(product.id, "active", !product.active); pages.refresh() } }
                    item { Action(if (product.promoted) "Quitar promoción pública" else "Promoción pública", !operation.loading) { vm.state(product.id, "promotion", !product.promoted) } }
                    item { Action(if (product.promocionWhatsapp) "Quitar promoción WhatsApp" else "Promoción WhatsApp", !operation.loading) { vm.state(product.id, "whatsapp-promotion", !product.promocionWhatsapp) } }
                }
            }
            item { ErrorCard(operation.error) }
        }
    }
    form?.let { FormDialog(forms, it) }
}
