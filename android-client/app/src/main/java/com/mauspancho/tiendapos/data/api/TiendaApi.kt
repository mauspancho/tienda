package com.mauspancho.tiendapos.data.api

import com.mauspancho.tiendapos.data.dto.*
import kotlinx.serialization.json.*
import okhttp3.*
import retrofit2.Call
import retrofit2.http.*

interface AuthApi {
    @GET("api/v1/health") suspend fun health(): Health
    @POST("api/v1/auth/login") suspend fun login(@Body request: LoginRequest): Session
    @POST("api/v1/auth/refresh") fun refresh(@Body request: RefreshRequest): Call<Session>
    @POST("api/v1/auth/logout") suspend fun logout(@Body request: RefreshRequest): retrofit2.Response<Unit>
}
interface TiendaApi {
    @GET("api/v1/whatsapp") suspend fun whatsapp(): WhatsappState
    @POST("api/v1/whatsapp/{action}") suspend fun whatsappAction(@Path("action") action: String): WhatsappState
    @Streaming @GET("api/v1/whatsapp/qr") suspend fun whatsappQr(): ResponseBody
    @GET("api/v1/whatsapp/groups/sync") suspend fun whatsappSync(): WhatsappSync
    @POST("api/v1/whatsapp/groups/sync") suspend fun syncWhatsappGroups(): WhatsappSync
    @PUT("api/v1/whatsapp/announcements/groups") suspend fun whatsappGroups(@Body value: WhatsappSelection): WhatsappState
    @PUT("api/v1/whatsapp/announcements/enabled") suspend fun whatsappAnnouncements(@Body value: Enabled): WhatsappState
    @GET("api/v1/whatsapp/products/{id}/preview") suspend fun promotionPreview(@Path("id") id: Long): PromotionPreview
    @POST("api/v1/whatsapp/products/{id}/promotions") suspend fun sendPromotion(@Path("id") id: Long,
        @Header("Idempotency-Key") key: String, @Body value: PromotionRequest): PromotionResult
    @GET("api/v1/auth/me") suspend fun me(): User
    @GET("api/v1/dashboard") suspend fun dashboard(): Dashboard
    @GET("api/v1/products") suspend fun products(@Query("q") q: String, @Query("page") page: Int,
        @Query("size") size: Int = 20, @Query("active") active: Boolean? = null, @Query("brand") brand: String? = null, @Query("whatsapp") whatsapp: Boolean? = null): Page<Product>
    @GET("api/v1/products/{id}") suspend fun product(@Path("id") id: Long): Product
    @GET("api/v1/products/barcode/{barcode}") suspend fun barcode(@Path("barcode") code: String): Product
    @POST("api/v1/products") suspend fun createProduct(@Body request: ProductRequest): Product
    @PUT("api/v1/products/{id}") suspend fun updateProduct(@Path("id") id: Long, @Body request: ProductRequest): Product
    @Multipart @PUT("api/v1/products/{id}") suspend fun productImage(@Path("id") id: Long,
        @Part("product") product: RequestBody, @Part image: MultipartBody.Part): Product
    @PATCH("api/v1/products/{id}/{action}") suspend fun productState(@Path("id") id: Long,
        @Path("action") action: String, @Body request: Enabled): Product
    @GET("api/v1/pos") suspend fun pos(): PosState
    @GET("api/v1/customers") suspend fun customers(@Query("q") q: String, @Query("page") page: Int, @Query("size") size: Int = 20): Page<Customer>
    @GET("api/v1/customers/{id}") suspend fun customer(@Path("id") id: Long): Customer
    @POST("api/v1/customers") suspend fun createCustomer(@Body request: CustomerRequest): Customer
    @PUT("api/v1/customers/{id}") suspend fun updateCustomer(@Path("id") id: Long, @Body request: CustomerRequest): Customer
    @GET("api/v1/credits/summary") suspend fun creditSummary(): CreditSummary
    @GET("api/v1/credits/customers") suspend fun creditCustomers(@Query("q") q: String, @Query("filter") filter: String,
        @Query("page") page: Int, @Query("size") size: Int = 20, @Query("sort") sort: String = "DEBT"): Page<CreditCustomer>
    @GET("api/v1/credits/customers/{id}") suspend fun creditStatement(@Path("id") id: Long): CreditStatement
    @POST("api/v1/credits/customers/{id}/payments") suspend fun creditPayment(@Path("id") id: Long,
        @Header("Idempotency-Key") key: String, @Body request: CreditPaymentRequest): CreditPaymentResult
    @POST("api/v1/credits/sales/{id}/cancel") suspend fun cancelCredit(@Path("id") id: Long, @Body request: CreditReason): retrofit2.Response<Unit>
    @POST("api/v1/credits/payments/{id}/reverse") suspend fun reverseCreditPayment(@Path("id") id: Long, @Body request: CreditReason): retrofit2.Response<Unit>
    @POST("api/v1/pos/checkout") suspend fun checkout(@Header("Idempotency-Key") key: String, @Body request: CheckoutRequest): Sale
    @GET("api/v1/tickets/{folio}") suspend fun ticket(@Path("folio") folio: String): Sale
    @GET("api/v1/cash/current") suspend fun cash(): CurrentCash
    @POST("api/v1/cash/open") suspend fun openCash(@Body request: OpenCash): CashSession
    @POST("api/v1/cash/{id}/close") suspend fun closeCash(@Path("id") id: Long, @Body request: CloseCash): CashSession
    // Paths here are selected only from the inspected contract in ModuleRepository; never from user input.
    @GET suspend fun read(@Url path: String, @QueryMap query: Map<String, String> = emptyMap()): JsonElement
    @POST suspend fun create(@Url path: String, @Body request: JsonObject,
        @Header("Idempotency-Key") key: String? = null): JsonElement
    @PUT suspend fun update(@Url path: String, @Body request: JsonObject): JsonElement
    @PATCH suspend fun patch(@Url path: String, @Body request: JsonObject): JsonElement
    @DELETE suspend fun delete(@Url path: String): retrofit2.Response<Unit>
    @Streaming @GET suspend fun download(@Url path: String, @QueryMap query: Map<String, String> = emptyMap()): ResponseBody
}
