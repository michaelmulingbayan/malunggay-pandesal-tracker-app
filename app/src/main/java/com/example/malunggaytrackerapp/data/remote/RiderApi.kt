package com.example.malunggaytrackerapp.data.remote

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** Admin-only rider endpoints. */
interface RiderApi {

    @GET("api/v1/riders")
    suspend fun list(): Response<DataResponse<List<RiderAccountDto>>>

    @POST("api/v1/riders")
    suspend fun create(@Body body: CreateRiderRequest): Response<DataResponse<RiderAccountDto>>

    @PATCH("api/v1/riders/{rider}/status")
    suspend fun updateStatus(
        @Path("rider") riderId: Long,
        @Body body: UpdateRiderStatusRequest,
    ): Response<DataResponse<RiderAccountDto>>
}

/** A rider as seen by an admin: profile plus the account's active state. */
@Keep
data class RiderAccountDto(
    @SerializedName("id") val id: Long,
    @SerializedName("user_id") val userId: Long,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("address") val address: String,
    @SerializedName("availability_status") val availabilityStatus: String,
    @SerializedName("is_active") val isActive: Boolean,
)

@Keep
data class CreateRiderRequest(
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("address") val address: String,
)

@Keep
data class UpdateRiderStatusRequest(
    @SerializedName("is_active") val isActive: Boolean,
)
