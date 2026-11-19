package com.example.malunggaytrackerapp.data.remote

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

/** Rider self-service: the signed-in rider's own stock, sales and returns. */
interface MyStockApi {

    @GET("api/v1/me/stock")
    suspend fun today(): Response<DataResponse<MyStockDto>>

    /** Sells N pieces at the current price; responds with the updated summary. */
    @POST("api/v1/me/sales")
    suspend fun sell(@Body body: SellRequest): Response<DataResponse<MyStockDto>>

    /** Returns N unsold pieces (pending until verified); responds with the updated summary. */
    @POST("api/v1/me/returns")
    suspend fun returnStock(@Body body: ReturnRequest): Response<DataResponse<MyStockDto>>

    @PATCH("api/v1/me/availability")
    suspend fun setAvailability(@Body body: AvailabilityRequest): Response<DataResponse<MyStockDto>>

    @POST("api/v1/me/locations")
    suspend fun shareLocation(@Body body: LocationRequest): Response<DataResponse<LocationDto>>
}

@Keep
data class LocationDto(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    /** UTC timestamp. */
    @SerializedName("recorded_at") val recordedAt: String,
)

@Keep
data class AvailabilityRequest(@SerializedName("is_available") val isAvailable: Boolean)

@Keep
data class LocationRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
)

/** Money fields are exact 2-decimal strings from the server (e.g. "450.00"). */
@Keep
data class MyStockDto(
    @SerializedName("date") val date: String,
    @SerializedName("is_available") val isAvailable: Boolean,
    /** Last shared GPS point, or null if the rider never shared one. */
    @SerializedName("last_location") val lastLocation: LocationDto?,
    /** Null when the admin hasn't set a price yet. */
    @SerializedName("price_per_piece") val pricePerPiece: String?,
    @SerializedName("assigned") val assigned: Int,
    @SerializedName("sold") val sold: Int,
    @SerializedName("returned") val returned: Int,
    @SerializedName("remaining") val remaining: Int,
    @SerializedName("sales_amount") val salesAmount: String,
    @SerializedName("sales") val sales: List<MySaleDto>,
    @SerializedName("returns") val returns: List<MyReturnDto>,
)

@Keep
data class MySaleDto(
    @SerializedName("id") val id: Long,
    @SerializedName("quantity_sold") val quantitySold: Int,
    @SerializedName("price_per_piece") val pricePerPiece: String,
    @SerializedName("total_amount") val totalAmount: String,
    /** UTC timestamp. */
    @SerializedName("sale_date") val saleDate: String,
)

@Keep
data class MyReturnDto(
    @SerializedName("id") val id: Long,
    @SerializedName("quantity_returned") val quantityReturned: Int,
    /** "pending", "verified" or "rejected". */
    @SerializedName("status") val status: String,
    /** UTC timestamp. */
    @SerializedName("return_date") val returnDate: String,
)

@Keep
data class SellRequest(@SerializedName("quantity_sold") val quantitySold: Int)

@Keep
data class ReturnRequest(@SerializedName("quantity_returned") val quantityReturned: Int)
