package com.example.malunggaytrackerapp.data

import com.example.malunggaytrackerapp.data.remote.InventoryApi
import com.example.malunggaytrackerapp.data.remote.InventorySummaryDto
import com.example.malunggaytrackerapp.data.remote.StockReturnDto

class InventoryRepository(private val api: InventoryApi) {

    suspend fun todaySummary(): ApiResult<InventorySummaryDto> =
        when (val result = safeApiCall { api.today() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }

    suspend fun returns(): ApiResult<List<StockReturnDto>> =
        when (val result = safeApiCall { api.returns() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }

    suspend fun verify(stockReturnId: Long): ApiResult<StockReturnDto> =
        when (val result = safeApiCall { api.verify(stockReturnId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }
}
