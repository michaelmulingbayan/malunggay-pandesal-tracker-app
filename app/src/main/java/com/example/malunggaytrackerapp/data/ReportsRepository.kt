package com.example.malunggaytrackerapp.data

import com.example.malunggaytrackerapp.data.remote.DataResponse
import com.example.malunggaytrackerapp.data.remote.ReportPeriodDto
import com.example.malunggaytrackerapp.data.remote.ReportsApi
import retrofit2.Response

/** The five detailed admin reports. [period] is "daily", "weekly" or "monthly"; [date] null means today. */
class ReportsRepository(private val api: ReportsApi) {

    suspend fun production(period: String, date: String?) = fetch { api.production(period, date) }

    suspend fun sales(period: String, date: String?) = fetch { api.sales(period, date) }

    suspend fun inventory(period: String, date: String?) = fetch { api.inventory(period, date) }

    suspend fun distribution(period: String, date: String?) = fetch { api.distribution(period, date) }

    suspend fun riders(period: String, date: String?) = fetch { api.riders(period, date) }

    private suspend fun <T : ReportPeriodDto> fetch(request: suspend () -> Response<DataResponse<T>>): ApiResult<T> =
        when (val result = safeApiCall(request)) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }
}
