package com.mauspancho.tiendapos.data.storage

import android.content.Context
import android.net.Uri
import com.mauspancho.tiendapos.data.api.ApiClient
import com.mauspancho.tiendapos.data.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import java.io.File

class MediaStorage(private val context: Context) {
    suspend fun upload(client: ApiClient, product: Product, uri: Uri): Product = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        require(mime in listOf("image/jpeg", "image/png", "image/webp")) { "Selecciona una imagen JPEG, PNG o WebP." }
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= 5 * 1024 * 1024) { "La imagen debe pesar menos de 5 MB." }; output.write(buffer, 0, count) }
            output.toByteArray()
        } ?: throw IllegalArgumentException("No se pudo abrir la imagen.")
        val latest = client.api.product(product.id)
        val request = ProductRequest(latest.code, latest.barcode, latest.name, latest.brand, latest.presentation,
            latest.imageUrl, latest.description, latest.category?.id, latest.purchaseCost, latest.salePrice,
            latest.currentStock, latest.minimumStock, latest.unit, latest.supplier?.id, latest.tax, latest.active)
        val extension = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        client.api.productImage(product.id, client.json.encodeToString(request).toRequestBody("application/json".toMediaType()),
            MultipartBody.Part.createFormData("image", "product.$extension", bytes.toRequestBody(mime.toMediaType())))
    }
    fun cameraFile(): File = File(context.cacheDir, "shared").apply { mkdirs() }.resolve("photo-${java.util.UUID.randomUUID()}.jpg")
    suspend fun financeCsv(client: ApiClient, filters: Map<String, String>): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "shared").apply { mkdirs() }.resolve("finanzas-${java.util.UUID.randomUUID()}.csv")
        try { client.api.download("api/v1/finances/daily.csv", filters).use { response -> response.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } } }; file }
        catch (e: Exception) { file.delete(); throw e }
    }
    suspend fun ticket(sale: Sale): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "shared").apply { mkdirs() }.resolve("ticket-${java.util.UUID.randomUUID()}.txt")
        file.writeText(buildString {
            appendLine("Tienda POS"); appendLine("Folio: ${sale.folio}"); appendLine(sale.saleDate)
            sale.items.forEach { appendLine("${it.productName} · ${it.quantity} × ${it.unitPrice} = ${it.subtotal}") }
            appendLine("Subtotal: ${sale.subtotal}"); appendLine("Impuestos: ${sale.tax}"); appendLine("Descuento: ${sale.discount}")
            appendLine("Total: ${sale.total}"); sale.payment?.let { appendLine("Pago: ${it.method}"); appendLine("Recibido: ${it.receivedAmount}"); appendLine("Cambio: ${it.changeAmount}") }
            if (sale.settlementType == "CREDIT") {
                appendLine("Venta a crédito"); appendLine("Cliente: ${sale.customer?.name.orEmpty()}")
                appendLine("Abonado: ${sale.amountPaid ?: java.math.BigDecimal.ZERO}"); appendLine("Pendiente: ${sale.pendingBalance}")
            }
        }); file
    }
    suspend fun clear() = withContext(Dispatchers.IO) {
        // Delete only files created by this client, directly inside its own cache/shared directory.
        File(context.cacheDir, "shared").listFiles()?.filter { it.isFile }?.forEach { it.delete() }
    }
}
