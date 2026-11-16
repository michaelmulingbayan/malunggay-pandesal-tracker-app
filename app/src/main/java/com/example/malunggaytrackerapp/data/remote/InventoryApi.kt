package com.example.malunggaytrackerapp.data.remote

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Admin-only inventory and stock-return endpoints. */
interface InventoryApi {

    /** Today's produced / distributed / returned totals. */
    @GET("api/v1/inventory")
    suspend fun today(): Response<DataResponse<InventorySummaryDto>>

    /** Returns: pending first, then newest. */
    @GET("api/v1/stock-returns")
    suspend fun returns(): Response<DataResponse<List<StockReturnDto>>>

    @POST("api/v1/stock-returns/{stockReturn}/verification")
    suspend fun verify(@Path("stockReturn") stockReturnId: Long): Response<DataResponse<StockReturnDto>>
}

@Keep
data class InventorySummaryDto(
    @SerializedName("date") val date: String,
    @SerializedName("produced") val produced: Int,
    @SerializedName("distributed") val distributed: Int,
    /** Pieces actually recorded as sold (from sales). */
    @SerializedName("sold") val sold: Int,
    @SerializedName("returned") val returned: Int,
    /** Distributed but not yet sold or returned. */
    @SerializedName("with_riders") val withRiders: Int,
    @SerializedName("returned_verified") val returnedVerified: Int,
    @SerializedName("returned_pending") val returnedPending: Int,
    @SerializedName("undistributed") val undistributed: Int,
)

@Keep
data class StockReturnDto(
    @SerializedName("id") val id: Long,
    @SerializedName("rider") val rider: NamedRefDto,
    @SerializedName("quantity_distributed") val quantityDistributed: Int,
    @SerializedName("quantity_returned") val quantityReturned: Int,
    /** UTC timestamp, e.g. "2026-11-16T10:30:00.000000Z". */
    @SerializedName("return_date") val returnDate: String,
    /** "pending", "verified" or "rejected". */
    @SerializedName("status") val status: String,
    @SerializedName("verified_by") val verifiedBy: NamedRefDto?,
) {
    val isPending: Boolean get() = status == "pending"
    val isVerified: Boolean get() = status == "verified"
}
