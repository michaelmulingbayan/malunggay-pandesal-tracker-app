package com.example.malunggaytrackerapp.data

import com.example.malunggaytrackerapp.data.remote.PandesalPriceDto
import com.example.malunggaytrackerapp.data.remote.SalesApi
import com.example.malunggaytrackerapp.data.remote.SalesSummaryDto
import com.example.malunggaytrackerapp.data.remote.SetPriceRequest

class SalesRepository(private val api: SalesApi) {

    suspend fun todaySummary(): ApiResult<SalesSummaryDto> =
        when (val result = safeApiCall { api.todaySummary() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }

    /** The price in effect now, or null when no price has been set yet. */
    suspend fun currentPrice(): ApiResult<PandesalPriceDto?> =
        when (val result = safeApiCall { api.prices() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data.firstOrNull())
            else -> result.asFailure()
        }

    /** [price] is a plain decimal string such as "3.50". */
    suspend fun setPrice(price: String): ApiResult<PandesalPriceDto> =
        when (val result = safeApiCall { api.setPrice(SetPriceRequest(price)) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }
}
