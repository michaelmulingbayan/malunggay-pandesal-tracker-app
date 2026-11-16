package com.example.malunggaytrackerapp.data.remote

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Sales reporting and the admin-set price per piece. */
interface SalesApi {

    /** Today's sales totals and per-rider breakdown. */
    @GET("api/v1/sales/summary")
    suspend fun todaySummary(): Response<DataResponse<SalesSummaryDto>>

    /** Price history, newest first: the first item is the current price. */
    @GET("api/v1/prices")
    suspend fun prices(): Response<DataResponse<List<PandesalPriceDto>>>

    @POST("api/v1/prices")
    suspend fun setPrice(@Body body: SetPriceRequest): Response<DataResponse<PandesalPriceDto>>
}

@Keep
data class PandesalPriceDto(
    @SerializedName("id") val id: Long,
    /** Exact 2-decimal string, e.g. "3.50". */
    @SerializedName("price_per_piece") val pricePerPiece: String,
    @SerializedName("set_by") val setBy: NamedRefDto?,
    /** UTC timestamp of when this price was set. */
    @SerializedName("created_at") val createdAt: String,
)

@Keep
data class SetPriceRequest(
    /** Sent as a string ("3.50") so no floating-point rounding happens on the way. */
    @SerializedName("price_per_piece") val pricePerPiece: String,
)

/** Money fields are exact 2-decimal strings from the server (e.g. "1380.00"); parse with BigDecimal. */
@Keep
data class SalesSummaryDto(
    @SerializedName("date") val date: String,
    @SerializedName("total_amount") val totalAmount: String,
    @SerializedName("pieces_sold") val piecesSold: Int,
    @SerializedName("riders_count") val ridersCount: Int,
    @SerializedName("riders") val riders: List<RiderSalesDto>,
)

@Keep
data class RiderSalesDto(
    @SerializedName("rider_id") val riderId: Long,
    @SerializedName("rider_name") val riderName: String,
    @SerializedName("pieces_sold") val piecesSold: Int,
    @SerializedName("total_amount") val totalAmount: String,
    @SerializedName("sales_count") val salesCount: Int,
)
