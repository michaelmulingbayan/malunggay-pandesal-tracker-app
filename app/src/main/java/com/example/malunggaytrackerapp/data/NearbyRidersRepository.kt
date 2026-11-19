package com.example.malunggaytrackerapp.data

import com.example.malunggaytrackerapp.data.remote.NearbyRiderDto
import com.example.malunggaytrackerapp.data.remote.PublicApi

/** Customer side (no login): riders on the road right now. */
class NearbyRidersRepository(private val api: PublicApi) {

    suspend fun nearbyRiders(): ApiResult<List<NearbyRiderDto>> =
        when (val result = safeApiCall { api.nearbyRiders() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }
}
