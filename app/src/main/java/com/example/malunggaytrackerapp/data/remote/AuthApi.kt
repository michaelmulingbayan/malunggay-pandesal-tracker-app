package com.example.malunggaytrackerapp.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("api/v1/auth/me")
    suspend fun me(): Response<DataResponse<UserDto>>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<Unit>
}
