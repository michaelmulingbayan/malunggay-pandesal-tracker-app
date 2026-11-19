package com.example.malunggaytrackerapp.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.malunggaytrackerapp.R
import com.google.android.material.color.MaterialColors

/**
 * Map marker for a rider: a round badge with their initials and a white ring.
 * Emerald when available, grey when unavailable (always paired with the name in the info window,
 * so availability is never shown by color alone).
 */
object RiderMarkerIcon {

    fun create(context: Context, name: String, isAvailable: Boolean): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (40 * density).toInt()
        val ring = 3 * density
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val fillColor = if (isAvailable) {
            ContextCompat.getColor(context, R.color.emerald_700)
        } else {
            ContextCompat.getColor(context, R.color.neutral_500)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Soft shadow so markers stand out on busy map tiles.
        paint.color = 0x33000000
        canvas.drawCircle(center, center + density, center - density, paint)
        paint.color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF.toInt())
        canvas.drawCircle(center, center, center - density, paint)
        paint.color = fillColor
        canvas.drawCircle(center, center, center - density - ring, paint)

        paint.color = 0xFFFFFFFF.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        paint.textSize = 14 * density
        val text = initialsOf(name)
        val baseline = center - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(text, center, baseline, paint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
