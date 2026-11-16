package com.example.malunggaytrackerapp

import android.app.Application
import com.example.malunggaytrackerapp.data.AuthRepository
import com.example.malunggaytrackerapp.data.RiderRepository
import com.example.malunggaytrackerapp.data.local.TokenStore
import com.example.malunggaytrackerapp.data.remote.ApiClient

/**
 * Holds the app-wide singletons. Access with `(application as MalunggayApp).authRepository`.
 */
class MalunggayApp : Application() {

    lateinit var authRepository: AuthRepository
        private set

    lateinit var riderRepository: RiderRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val tokenStore = TokenStore(this)
        val apiClient = ApiClient(tokenStore)
        authRepository = AuthRepository(apiClient.authApi, tokenStore)
        riderRepository = RiderRepository(apiClient.riderApi)
    }
}
