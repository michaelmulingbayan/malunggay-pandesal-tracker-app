package com.example.malunggaytrackerapp.data.remote

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("device_name") val deviceName: String,
)

@Keep
data class LoginResponse(
    @SerializedName("token") val token: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user") val user: UserDto,
)

/** Wrapper used by Laravel API resources: `{ "data": { ... } }`. */
@Keep
data class DataResponse<T>(
    @SerializedName("data") val data: T,
)

@Keep
data class UserDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("role") val role: String,
    @SerializedName("status") val status: String,
    @SerializedName("rider") val rider: RiderDto?,
) {
    val isAdmin: Boolean get() = role == "admin"
}

@Keep
data class RiderDto(
    @SerializedName("id") val id: Long,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("address") val address: String,
    @SerializedName("availability_status") val availabilityStatus: String,
)

/** Laravel error body: `{ "message": "...", "errors": { "field": ["..."] } }`. */
@Keep
data class ApiErrorResponse(
    @SerializedName("message") val message: String?,
    @SerializedName("errors") val errors: Map<String, List<String>>?,
)
