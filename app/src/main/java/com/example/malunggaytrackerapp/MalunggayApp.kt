package com.example.malunggaytrackerapp

import android.app.Application
import com.example.malunggaytrackerapp.data.AuthRepository
import com.example.malunggaytrackerapp.data.InventoryRepository
import com.example.malunggaytrackerapp.data.MyStockRepository
import com.example.malunggaytrackerapp.data.NearbyRidersRepository
import com.example.malunggaytrackerapp.data.ProductionRepository
import com.example.malunggaytrackerapp.data.ReportsRepository
import com.example.malunggaytrackerapp.data.RiderRepository
import com.example.malunggaytrackerapp.data.SalesRepository
import com.example.malunggaytrackerapp.data.StockRepository
import com.example.malunggaytrackerapp.data.local.TokenStore
import com.example.malunggaytrackerapp.data.remote.ApiClient
import org.osmdroid.config.Configuration
import java.io.File

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

    lateinit var myStockRepository: MyStockRepository
        private set

    lateinit var nearbyRidersRepository: NearbyRidersRepository
        private set

    lateinit var reportsRepository: ReportsRepository
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
        myStockRepository = MyStockRepository(apiClient.myStockApi)
        nearbyRidersRepository = NearbyRidersRepository(apiClient.publicApi)
        reportsRepository = ReportsRepository(apiClient.reportsApi)

        // OpenStreetMap (osmdroid): identify the app to the tile servers (their usage policy
        // requires it) and keep the tile cache in app-private storage (no storage permission).
        Configuration.getInstance().apply {
            userAgentValue = "MalunggayTracker/${BuildConfig.VERSION_NAME} (Android; $packageName)"
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }
}
