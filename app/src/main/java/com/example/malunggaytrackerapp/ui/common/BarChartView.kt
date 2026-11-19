package com.example.malunggaytrackerapp.ui.common

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.malunggaytrackerapp.R
import com.google.android.material.color.MaterialColors
import kotlin.math.max

/**
 * Simple vertical bar chart (Reports › Pieces sold): one rounded bar per day or month with its
 * label underneath. The selected bar (by default the last one: today / this month) is drawn in the
 * dark brand green with its value above it; tap any bar to see its value.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class Bar(val label: String, val value: Int, val valueLabel: String)

    private var bars: List<Bar> = emptyList()
    private var selected = -1
    private var growth = 1f
    private var animator: ValueAnimator? = null

    private val barPaint = fill(ContextCompat.getColor(context, R.color.chart_bar))
    private val currentPaint = fill(ContextCompat.getColor(context, R.color.chart_bar_current))
    private val labelPaint = text(
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant), sp(12f),
    )
    private val valuePaint = text(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface), sp(12f))
        .apply { isFakeBoldText = true }

    private val chartHeight = dp(150f)
    private val valueSpace = dp(22f)
    private val labelSpace = dp(26f)
    private val barGap = dp(8f)
    private val corner = dp(8f)
    private val minBar = dp(4f)
    private val rect = RectF()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** Shows [bars]; selects the last one. [description] is read by TalkBack. */
    fun setBars(bars: List<Bar>, description: String) {
        val changed = bars != this.bars
        this.bars = bars
        selected = bars.lastIndex
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
        val wanted = (valueSpace + chartHeight + labelSpace + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(wanted, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return

        val left = paddingLeft.toFloat()
        val width = (this.width - paddingLeft - paddingRight).toFloat()
        val slot = width / bars.size
        // Many bars (a month) get thinner gaps and corners so the bars themselves stay visible.
        val gap = minOf(barGap, slot * 0.25f)
        val barWidth = slot - gap
        val radius = minOf(corner, barWidth / 2f)
        val baseline = paddingTop + valueSpace + chartHeight
        val maxValue = max(1, bars.maxOf { it.value }).toFloat()

        bars.forEachIndexed { index, bar ->
            val x = left + slot * index + gap / 2f
            val height = if (bar.value > 0) max(minBar, chartHeight * (bar.value / maxValue) * growth) else minBar
            rect.set(x, baseline - height, x + barWidth, baseline)
            canvas.drawRoundRect(rect, radius, radius, if (index == selected) currentPaint else barPaint)

            val centerX = x + barWidth / 2f
            canvas.drawText(bar.label, centerX, baseline + labelSpace - dp(6f), labelPaint)
            if (index == selected) {
                // Keep the number inside the view for the first / last of many thin bars.
                val half = valuePaint.measureText(bar.valueLabel) / 2f
                val textX = if (width > 2 * half) centerX.coerceIn(left + half, left + width - half) else centerX
                canvas.drawText(bar.valueLabel, textX, rect.top - dp(6f), valuePaint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility") // Tapping only changes which value is shown; all values are in contentDescription.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bars.isEmpty()) return false
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val slot = (width - paddingLeft - paddingRight).toFloat() / bars.size
            val index = ((event.x - paddingLeft) / slot).toInt().coerceIn(0, bars.lastIndex)
            if (index != selected) {
                selected = index
                invalidate()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun text(color: Int, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        textAlign = Paint.Align.CENTER
    }

    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
