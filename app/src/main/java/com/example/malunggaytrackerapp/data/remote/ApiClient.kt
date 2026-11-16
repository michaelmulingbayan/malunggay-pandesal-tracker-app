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

object ApiClient {

    fun createAuthApi(tokenStore: TokenStore): AuthApi = createRetrofit(tokenStore).create(AuthApi::class.java)

    private fun createRetrofit(tokenStore: TokenStore): Retrofit {
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

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
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
