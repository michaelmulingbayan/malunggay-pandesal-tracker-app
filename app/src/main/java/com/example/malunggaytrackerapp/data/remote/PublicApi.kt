package com.example.malunggaytrackerapp.data.remote

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET

/** Public endpoints (no login): used by customers. */
interface PublicApi {

    /** Available riders who shared a location in the last 30 minutes (first name only). */
    @GET("api/v1/public/riders/nearby")
    suspend fun nearbyRiders(): Response<DataResponse<List<NearbyRiderDto>>>
}

@Keep
data class NearbyRiderDto(
    @SerializedName("rider_id") val riderId: Long,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    /** UTC timestamp of the rider's latest shared location. */
    @SerializedName("recorded_at") val recordedAt: String,
)
