package com.example.malunggaytrackerapp.data

import com.example.malunggaytrackerapp.data.remote.AvailabilityRequest
import com.example.malunggaytrackerapp.data.remote.LocationDto
import com.example.malunggaytrackerapp.data.remote.LocationRequest
import com.example.malunggaytrackerapp.data.remote.MyStockApi
import com.example.malunggaytrackerapp.data.remote.MyStockDto
import com.example.malunggaytrackerapp.data.remote.ReturnRequest
import com.example.malunggaytrackerapp.data.remote.SellRequest

/** The signed-in rider's own stock, sales and returns. */
class MyStockRepository(private val api: MyStockApi) {

    suspend fun today(): ApiResult<MyStockDto> =
        when (val result = safeApiCall { api.today() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }

    suspend fun sell(quantity: Int): ApiResult<MyStockDto> =
        when (val result = safeApiCall { api.sell(SellRequest(quantity)) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }

    suspend fun returnStock(quantity: Int): ApiResult<MyStockDto> =
        when (val result = safeApiCall { api.returnStock(ReturnRequest(quantity)) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }

    suspend fun setAvailability(isAvailable: Boolean): ApiResult<MyStockDto> =
        when (val result = safeApiCall { api.setAvailability(AvailabilityRequest(isAvailable)) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }

    suspend fun shareLocation(latitude: Double, longitude: Double): ApiResult<LocationDto> =
        when (val result = safeApiCall { api.shareLocation(LocationRequest(latitude, longitude)) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }
}
