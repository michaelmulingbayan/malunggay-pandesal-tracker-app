package com.example.malunggaytrackerapp.data

import com.example.malunggaytrackerapp.data.remote.CreateRiderRequest
import com.example.malunggaytrackerapp.data.remote.RiderAccountDto
import com.example.malunggaytrackerapp.data.remote.RiderApi
import com.example.malunggaytrackerapp.data.remote.RiderPositionDto
import com.example.malunggaytrackerapp.data.remote.UpdateRiderStatusRequest

/** Admin operations on riders. */
class RiderRepository(private val api: RiderApi) {

    suspend fun riders(): ApiResult<List<RiderAccountDto>> =
        when (val result = safeApiCall { api.list() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }

    suspend fun addRider(request: CreateRiderRequest): ApiResult<RiderAccountDto> =
        when (val result = safeApiCall { api.create(request) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }

    suspend fun locations(): ApiResult<List<RiderPositionDto>> =
        when (val result = safeApiCall { api.locations() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }

    suspend fun setActive(riderId: Long, isActive: Boolean): ApiResult<RiderAccountDto> =
        when (val result = safeApiCall { api.updateStatus(riderId, UpdateRiderStatusRequest(isActive)) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }
}
