package com.example.malunggaytrackerapp.data

import com.example.malunggaytrackerapp.data.remote.ProductionApi
import com.example.malunggaytrackerapp.data.remote.ProductionDto
import com.example.malunggaytrackerapp.data.remote.RecordProductionRequest

class ProductionRepository(private val api: ProductionApi) {

    /** Most recent production batches, newest first. */
    suspend fun history(): ApiResult<List<ProductionDto>> =
        when (val result = safeApiCall { api.history() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }

    suspend fun record(request: RecordProductionRequest): ApiResult<ProductionDto> =
        when (val result = safeApiCall { api.record(request) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }
}
