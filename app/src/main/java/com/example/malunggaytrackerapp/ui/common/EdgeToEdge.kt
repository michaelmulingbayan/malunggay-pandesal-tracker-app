package com.example.malunggaytrackerapp.ui.common

import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Standard screen setup for the Malunggay design system:
 * the emerald [header] runs behind the status bar (with light status-bar icons),
 * and [root] keeps its content clear of the navigation bar and keyboard.
 */
fun ComponentActivity.setupBrandedEdgeToEdge(root: View, header: View) {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
    )

    val headerTop = header.paddingTop
    val rootBottom = root.paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

        header.updatePadding(top = headerTop + bars.top)
        v.updatePadding(
            left = bars.left,
            right = bars.right,
            bottom = rootBottom + maxOf(bars.bottom, ime.bottom),
        )
        insets
    }
}
