package com.example.malunggaytrackerapp.data.remote

import com.example.malunggaytrackerapp.BuildConfig
import com.example.malunggaytrackerapp.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** One Retrofit instance (and connection pool) shared by every API interface. */
class ApiClient(tokenStore: TokenStore) {

    private val retrofit: Retrofit = run {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .apply {
                if (BuildConfig.DEBUG) {
                    // Logs request/response lines only, never bodies, so passwords and tokens stay out of Logcat.
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val riderApi: RiderApi = retrofit.create(RiderApi::class.java)
    val productionApi: ProductionApi = retrofit.create(ProductionApi::class.java)
    val stockDistributionApi: StockDistributionApi = retrofit.create(StockDistributionApi::class.java)
    val inventoryApi: InventoryApi = retrofit.create(InventoryApi::class.java)
    val salesApi: SalesApi = retrofit.create(SalesApi::class.java)
    val myStockApi: MyStockApi = retrofit.create(MyStockApi::class.java)
    val reportsApi: ReportsApi = retrofit.create(ReportsApi::class.java)
}

/**
 * Adds `Accept: application/json` to every request (so Laravel returns JSON errors
 * instead of redirects) and the Sanctum bearer token when the user is logged in.
 * A 401 on an authenticated request means the token was revoked or expired, so it is cleared.
 */
private class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.token
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()

        val response = chain.proceed(request)
        if (response.code == 401 && token != null) {
            tokenStore.clear()
        }
        return response
    }
}
