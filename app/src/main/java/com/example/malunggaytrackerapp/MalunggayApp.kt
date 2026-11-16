package com.example.malunggaytrackerapp

import android.app.Application
import com.example.malunggaytrackerapp.data.AuthRepository
import com.example.malunggaytrackerapp.data.InventoryRepository
import com.example.malunggaytrackerapp.data.ProductionRepository
import com.example.malunggaytrackerapp.data.RiderRepository
import com.example.malunggaytrackerapp.data.SalesRepository
import com.example.malunggaytrackerapp.data.StockRepository
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

    lateinit var productionRepository: ProductionRepository
        private set

    lateinit var stockRepository: StockRepository
        private set

    lateinit var inventoryRepository: InventoryRepository
        private set

    lateinit var salesRepository: SalesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val tokenStore = TokenStore(this)
        val apiClient = ApiClient(tokenStore)
        authRepository = AuthRepository(apiClient.authApi, tokenStore)
        riderRepository = RiderRepository(apiClient.riderApi)
        productionRepository = ProductionRepository(apiClient.productionApi)
        stockRepository = StockRepository(apiClient.stockDistributionApi)
        inventoryRepository = InventoryRepository(apiClient.inventoryApi)
        salesRepository = SalesRepository(apiClient.salesApi)
    }
}
