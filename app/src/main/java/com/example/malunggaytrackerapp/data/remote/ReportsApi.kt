package com.example.malunggaytrackerapp.data.remote

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Detailed admin reports. [period] is "daily", "weekly" (Monday–Sunday) or "monthly"; [date] ("yyyy-MM-dd")
 * picks which day / week / month, today when null. Money fields are exact 2-decimal strings.
 */
interface ReportsApi {

    @GET("api/v1/reports/production")
    suspend fun production(@Query("period") period: String, @Query("date") date: String?): Response<DataResponse<ProductionReportDto>>

    @GET("api/v1/reports/sales")
    suspend fun sales(@Query("period") period: String, @Query("date") date: String?): Response<DataResponse<SalesReportDto>>

    @GET("api/v1/reports/inventory")
    suspend fun inventory(@Query("period") period: String, @Query("date") date: String?): Response<DataResponse<InventoryReportDto>>

    @GET("api/v1/reports/distribution")
    suspend fun distribution(@Query("period") period: String, @Query("date") date: String?): Response<DataResponse<DistributionReportDto>>

    @GET("api/v1/reports/riders")
    suspend fun riders(@Query("period") period: String, @Query("date") date: String?): Response<DataResponse<RiderPerformanceReportDto>>
}

/** Fields every report starts with: the period and its first / last day. */
interface ReportPeriodDto {
    val period: String
    val date: String
    val from: String
    val to: String
}

// ===== Production =====

@Keep
data class ProductionReportDto(
    @SerializedName("period") override val period: String,
    @SerializedName("date") override val date: String,
    @SerializedName("from") override val from: String,
    @SerializedName("to") override val to: String,
    @SerializedName("summary") val summary: ProductionReportSummaryDto,
    @SerializedName("daily") val daily: List<ProducedDayDto>,
    @SerializedName("batches") val batches: List<ProductionBatchDto>,
) : ReportPeriodDto

@Keep
data class ProductionReportSummaryDto(
    @SerializedName("produced") val produced: Int,
    @SerializedName("batches") val batches: Int,
    @SerializedName("production_days") val productionDays: Int,
    @SerializedName("average_per_production_day") val averagePerProductionDay: Int,
    @SerializedName("distributed") val distributed: Int,
    @SerializedName("undistributed") val undistributed: Int,
)

@Keep
data class ProducedDayDto(
    @SerializedName("date") val date: String,
    @SerializedName("produced") val produced: Int,
)

@Keep
data class ProductionBatchDto(
    @SerializedName("id") val id: Long,
    @SerializedName("production_date") val productionDate: String,
    @SerializedName("quantity_produced") val quantityProduced: Int,
    @SerializedName("distributed") val distributed: Int,
    @SerializedName("remarks") val remarks: String?,
    @SerializedName("recorded_by") val recordedBy: String?,
)

// ===== Sales =====

@Keep
data class SalesReportDto(
    @SerializedName("period") override val period: String,
    @SerializedName("date") override val date: String,
    @SerializedName("from") override val from: String,
    @SerializedName("to") override val to: String,
    @SerializedName("summary") val summary: SalesReportSummaryDto,
    @SerializedName("daily") val daily: List<SalesDayDto>,
    @SerializedName("riders") val riders: List<RiderSalesDto>,
) : ReportPeriodDto

@Keep
data class SalesReportSummaryDto(
    @SerializedName("total_amount") val totalAmount: String,
    @SerializedName("pieces_sold") val piecesSold: Int,
    @SerializedName("sales_count") val salesCount: Int,
    @SerializedName("riders_count") val ridersCount: Int,
    @SerializedName("average_price") val averagePrice: String,
)

@Keep
data class SalesDayDto(
    @SerializedName("date") val date: String,
    @SerializedName("pieces_sold") val piecesSold: Int,
    @SerializedName("total_amount") val totalAmount: String,
)

// ===== Inventory =====

@Keep
data class InventoryReportDto(
    @SerializedName("period") override val period: String,
    @SerializedName("date") override val date: String,
    @SerializedName("from") override val from: String,
    @SerializedName("to") override val to: String,
    @SerializedName("summary") val summary: InventoryReportSummaryDto,
    @SerializedName("daily") val daily: List<InventoryDayDto>,
) : ReportPeriodDto

@Keep
data class InventoryReportSummaryDto(
    @SerializedName("produced") val produced: Int,
    @SerializedName("distributed") val distributed: Int,
    @SerializedName("sold") val sold: Int,
    @SerializedName("returned") val returned: Int,
    @SerializedName("returned_verified") val returnedVerified: Int,
    @SerializedName("returned_pending") val returnedPending: Int,
    @SerializedName("returned_rejected") val returnedRejected: Int,
    @SerializedName("with_riders") val withRiders: Int,
    @SerializedName("undistributed") val undistributed: Int,
    @SerializedName("sell_through_percent") val sellThroughPercent: Int,
)

@Keep
data class InventoryDayDto(
    @SerializedName("date") val date: String,
    @SerializedName("produced") val produced: Int,
    @SerializedName("distributed") val distributed: Int,
    @SerializedName("sold") val sold: Int,
    @SerializedName("returned") val returned: Int,
)

// ===== Stock distribution =====

@Keep
data class DistributionReportDto(
    @SerializedName("period") override val period: String,
    @SerializedName("date") override val date: String,
    @SerializedName("from") override val from: String,
    @SerializedName("to") override val to: String,
    @SerializedName("summary") val summary: DistributionReportSummaryDto,
    @SerializedName("daily") val daily: List<DistributedDayDto>,
    @SerializedName("riders") val riders: List<RiderDistributionDto>,
    @SerializedName("assignments") val assignments: List<AssignmentReportDto>,
) : ReportPeriodDto

@Keep
data class DistributionReportSummaryDto(
    @SerializedName("distributed") val distributed: Int,
    @SerializedName("assignments") val assignments: Int,
    @SerializedName("riders_count") val ridersCount: Int,
    @SerializedName("sold") val sold: Int,
    @SerializedName("returned") val returned: Int,
    @SerializedName("with_riders") val withRiders: Int,
)

@Keep
data class DistributedDayDto(
    @SerializedName("date") val date: String,
    @SerializedName("distributed") val distributed: Int,
)

@Keep
data class RiderDistributionDto(
    @SerializedName("rider_id") val riderId: Long,
    @SerializedName("rider_name") val riderName: String,
    @SerializedName("assignments") val assignments: Int,
    @SerializedName("distributed") val distributed: Int,
    @SerializedName("sold") val sold: Int,
    @SerializedName("returned") val returned: Int,
    @SerializedName("remaining") val remaining: Int,
)

@Keep
data class AssignmentReportDto(
    @SerializedName("id") val id: Long,
    @SerializedName("distribution_date") val distributionDate: String,
    @SerializedName("rider_name") val riderName: String,
    @SerializedName("distributed") val distributed: Int,
    @SerializedName("sold") val sold: Int,
    @SerializedName("returned") val returned: Int,
    @SerializedName("remaining") val remaining: Int,
)

// ===== Rider performance =====

@Keep
data class RiderPerformanceReportDto(
    @SerializedName("period") override val period: String,
    @SerializedName("date") override val date: String,
    @SerializedName("from") override val from: String,
    @SerializedName("to") override val to: String,
    @SerializedName("summary") val summary: RiderPerformanceSummaryDto,
    @SerializedName("riders") val riders: List<RiderPerformanceDto>,
) : ReportPeriodDto

@Keep
data class RiderPerformanceSummaryDto(
    @SerializedName("riders_count") val ridersCount: Int,
    @SerializedName("assigned") val assigned: Int,
    @SerializedName("sold") val sold: Int,
    @SerializedName("sales_amount") val salesAmount: String,
    @SerializedName("sell_through_percent") val sellThroughPercent: Int,
    @SerializedName("top_rider_name") val topRiderName: String?,
)

@Keep
data class RiderPerformanceDto(
    @SerializedName("rider_id") val riderId: Long,
    @SerializedName("rider_name") val riderName: String,
    @SerializedName("days_active") val daysActive: Int,
    @SerializedName("assigned") val assigned: Int,
    @SerializedName("sold") val sold: Int,
    @SerializedName("returned") val returned: Int,
    @SerializedName("remaining") val remaining: Int,
    @SerializedName("sales_amount") val salesAmount: String,
    @SerializedName("sell_through_percent") val sellThroughPercent: Int,
    @SerializedName("average_sold_per_day") val averageSoldPerDay: Int,
)
