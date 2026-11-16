package com.example.malunggaytrackerapp.data.remote

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Admin-only stock distribution endpoints. */
interface StockDistributionApi {

    /** Today's assignments plus today's stock summary. */
    @GET("api/v1/stock-distributions")
    suspend fun today(): Response<StockDistributionsResponse>

    @POST("api/v1/stock-distributions")
    suspend fun assign(@Body body: AssignStockRequest): Response<StockDistributionsResponse>
}

@Keep
data class StockDistributionsResponse(
    @SerializedName("data") val data: List<StockDistributionDto>,
    @SerializedName("meta") val meta: StockSummaryDto,
)

@Keep
data class StockDistributionDto(
    @SerializedName("id") val id: Long,
    @SerializedName("rider") val rider: NamedRefDto,
    @SerializedName("quantity_distributed") val quantityDistributed: Int,
    @SerializedName("distribution_date") val distributionDate: String,
    @SerializedName("distributed_by") val distributedBy: NamedRefDto?,
    /** ISO-8601 UTC timestamp, e.g. "2026-11-16T04:59:32.000000Z". */
    @SerializedName("created_at") val createdAt: String,
)

@Keep
data class NamedRefDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
)

@Keep
data class StockSummaryDto(
    @SerializedName("date") val date: String,
    @SerializedName("produced") val produced: Int,
    @SerializedName("distributed") val distributed: Int,
    @SerializedName("available") val available: Int,
)

@Keep
data class AssignStockRequest(
    @SerializedName("rider_id") val riderId: Long,
    @SerializedName("quantity") val quantity: Int,
)
