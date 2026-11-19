package com.example.malunggaytrackerapp.data.remote

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Admin-only production endpoints. */
interface ProductionApi {

    @GET("api/v1/productions")
    suspend fun history(@Query("per_page") perPage: Int = 30): Response<DataResponse<List<ProductionDto>>>

    @POST("api/v1/productions")
    suspend fun record(@Body body: RecordProductionRequest): Response<DataResponse<ProductionDto>>
}

@Keep
data class ProductionDto(
    @SerializedName("id") val id: Long,
    /** ISO date, e.g. "2026-11-16". */
    @SerializedName("production_date") val productionDate: String,
    @SerializedName("quantity_produced") val quantityProduced: Int,
    @SerializedName("remarks") val remarks: String?,
    @SerializedName("recorded_by") val recordedBy: RecorderDto?,
)

@Keep
data class RecorderDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
)

@Keep
data class RecordProductionRequest(
    @SerializedName("production_date") val productionDate: String,
    @SerializedName("quantity_produced") val quantityProduced: Int,
    @SerializedName("remarks") val remarks: String?,
)
