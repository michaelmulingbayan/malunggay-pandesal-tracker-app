package com.example.malunggaytrackerapp.data

import com.example.malunggaytrackerapp.data.remote.AssignStockRequest
import com.example.malunggaytrackerapp.data.remote.StockDistributionApi
import com.example.malunggaytrackerapp.data.remote.StockDistributionsResponse

class StockRepository(private val api: StockDistributionApi) {

    /** Today's assignments and stock summary (produced / distributed / available). */
    suspend fun today(): ApiResult<StockDistributionsResponse> = safeApiCall { api.today() }

    /** Assigns stock to a rider; the response carries the updated summary. */
    suspend fun assign(riderId: Long, quantity: Int): ApiResult<StockDistributionsResponse> =
        safeApiCall { api.assign(AssignStockRequest(riderId, quantity)) }
}
