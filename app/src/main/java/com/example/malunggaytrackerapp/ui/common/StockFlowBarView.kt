package com.example.malunggaytrackerapp.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.malunggaytrackerapp.R
import com.google.android.material.color.MaterialColors
import kotlin.math.max

/**
 * One progress-style bar for the day's stock flow.
 *
 *  [ dark: sold ][ light: returned ][ green: rest of production ]
 *
 * The full track is everything **produced** (green). From the left, the dark segment is
 * what was **sold** and the light segment what was **returned**. Segments are separated by a
 * 2dp surface gap (dataviz spec). Colors come from resources with separate light/dark-mode
 * steps (`chart_produced`, `chart_sold`, `chart_returned`) and match the legend dots.
 */
class StockFlowBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var produced = 0
    private var sold = 0
    private var returned = 0
    private var growth = 1f
    private var animator: ValueAnimator? = null

    private val producedPaint = paint(ContextCompat.getColor(context, R.color.chart_produced))
    private val soldPaint = paint(ContextCompat.getColor(context, R.color.chart_sold))
    private val returnedPaint = paint(ContextCompat.getColor(context, R.color.chart_returned))
    private val gapPaint = paint(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))

    private val clip = Path()
    private val bounds = RectF()
    private val barHeight = dp(20f)
    private val gap = dp(2f)
    private val minSegment = dp(4f)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setValues(produced: Int, sold: Int, returned: Int, description: String) {
        val newProduced = max(0, produced)
        val newSold = max(0, sold)
        val newReturned = max(0, returned)
        val changed = newProduced != this.produced || newSold != this.sold || newReturned != this.returned

        this.produced = newProduced
        this.sold = newSold
        this.returned = newReturned
        contentDescription = description

        animator?.cancel()
        if (changed) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    growth = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = resolveSize((barHeight + paddingTop + paddingBottom).toInt(), heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val top = paddingTop + (h - paddingTop - paddingBottom - barHeight) / 2f
        bounds.set(paddingLeft.toFloat(), top, (w - paddingRight).toFloat(), top + barHeight)
        clip.reset()
        clip.addRoundRect(bounds, barHeight / 2f, barHeight / 2f, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        canvas.clipPath(clip)

        // Track = everything produced.
        canvas.drawRect(bounds, producedPaint)

        val total = max(1, max(produced, sold + returned)).toFloat()
        val width = bounds.width()

        var cursor = bounds.left
        cursor = drawSegment(canvas, cursor, width * (sold / total) * growth, sold, soldPaint)
        cursor = drawSegment(canvas, cursor, width * (returned / total) * growth, returned, returnedPaint)

        canvas.restoreToCount(save)
    }

    /** Draws one segment from [start] and a surface gap after it; returns where the next segment starts. */
    private fun drawSegment(canvas: Canvas, start: Float, length: Float, value: Int, paint: Paint): Float {
        if (value <= 0) return start
        // Keep small non-zero values visible so "few" never reads as "none".
        val end = minOf(bounds.right, start + max(length, minSegment))
        canvas.drawRect(start, bounds.top, end, bounds.bottom, paint)
        if (end < bounds.right) {
            canvas.drawRect(end, bounds.top, end + gap, bounds.bottom, gapPaint)
        }
        return end + gap
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
}
