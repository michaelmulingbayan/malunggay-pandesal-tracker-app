package com.example.malunggaytrackerapp.ui.common

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.malunggaytrackerapp.BuildConfig

/**
 * Android 17 (API 37) requires the ACCESS_LOCAL_NETWORK runtime permission to reach
 * private addresses such as 10.0.2.2 (emulator → PC) or 192.168.x.x (phone → PC on Wi-Fi).
 * Without it, connections to the local Laravel server silently time out.
 *
 * Only relevant when the API base URL points at the local network (development);
 * a public HTTPS API never triggers the prompt.
 */
object LocalNetworkAccess {

    private const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    private const val API_37 = 37

    val isRequiredForApi: Boolean
        get() = Build.VERSION.SDK_INT >= API_37 && isLocalNetworkHost(Uri.parse(BuildConfig.API_BASE_URL).host)

    fun isGranted(activity: ComponentActivity): Boolean =
        !isRequiredForApi ||
            ContextCompat.checkSelfPermission(activity, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Registers the permission request (call in onCreate) and asks for it right away if needed.
     * [onResult] receives true when the API can be reached.
     */
    fun requestIfNeeded(activity: ComponentActivity, onResult: (granted: Boolean) -> Unit = {}) {
        val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
        if (!isGranted(activity)) {
            launcher.launch(PERMISSION)
        }
    }

    private fun isLocalNetworkHost(host: String?): Boolean {
        if (host == null) return false
        if (host == "localhost") return true
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        val (a, b) = parts
        return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31)
    }
}
