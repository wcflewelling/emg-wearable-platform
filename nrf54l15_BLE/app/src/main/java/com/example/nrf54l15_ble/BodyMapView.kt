package com.example.nrf54l15_ble

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Front-facing body silhouette with muscle-placement markers. Draws every muscle site
 * faintly and highlights the currently selected group in its colour — the Android analog
 * of the iOS `BodyMapView` body diagram.
 *
 * Coordinates are normalised (0..1) within the view so markers always land on the body.
 */
class BodyMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    /** A muscle group and its marker position(s) in normalised view space. */
    data class Site(val color: Int, val points: List<PointF>)

    var sites: List<Site> = emptyList()
        set(value) { field = value; invalidate() }

    var selectedIndex: Int = -1
        set(value) { field = value; invalidate() }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D7DBE2"); style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AEB6C2"); style = Paint.Style.FILL
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val r = 0.055f * w   // generic corner / limb radius

        // ── Silhouette ────────────────────────────────────────────────
        // Head
        canvas.drawCircle(0.5f * w, 0.075f * h, 0.052f * w, bodyPaint)
        // Neck
        roundRect(canvas, 0.46f, 0.115f, 0.54f, 0.175f, w, h, r, bodyPaint)
        // Shoulders
        roundRect(canvas, 0.28f, 0.18f, 0.72f, 0.30f, w, h, r, bodyPaint)
        // Arms
        roundRect(canvas, 0.195f, 0.21f, 0.305f, 0.47f, w, h, r, bodyPaint)
        roundRect(canvas, 0.695f, 0.21f, 0.805f, 0.47f, w, h, r, bodyPaint)
        // Torso
        roundRect(canvas, 0.36f, 0.23f, 0.64f, 0.54f, w, h, r, bodyPaint)
        // Pelvis
        roundRect(canvas, 0.37f, 0.49f, 0.63f, 0.585f, w, h, r, bodyPaint)
        // Legs
        roundRect(canvas, 0.385f, 0.55f, 0.487f, 0.90f, w, h, r, bodyPaint)
        roundRect(canvas, 0.513f, 0.55f, 0.615f, 0.90f, w, h, r, bodyPaint)

        // ── Markers ───────────────────────────────────────────────────
        // Unselected first, then the selected group on top.
        sites.forEachIndexed { i, site ->
            if (i == selectedIndex) return@forEachIndexed
            for (p in site.points) {
                canvas.drawCircle(p.x * w, p.y * h, 0.018f * w, dotPaint)
            }
        }
        if (selectedIndex in sites.indices) {
            val site = sites[selectedIndex]
            fillPaint.color = (site.color and 0x00FFFFFF) or 0x55000000
            ringPaint.color = site.color
            ringPaint.strokeWidth = 0.012f * w
            for (p in site.points) {
                canvas.drawCircle(p.x * w, p.y * h, 0.05f * w, fillPaint)
                canvas.drawCircle(p.x * w, p.y * h, 0.05f * w, ringPaint)
                canvas.drawCircle(p.x * w, p.y * h, 0.022f * w, ringPaint.apply { style = Paint.Style.FILL })
                ringPaint.style = Paint.Style.STROKE
            }
        }
    }

    private fun roundRect(
        c: Canvas, l: Float, t: Float, rr: Float, b: Float,
        w: Float, h: Float, radius: Float, paint: Paint
    ) {
        rect.set(l * w, t * h, rr * w, b * h)
        c.drawRoundRect(rect, radius, radius, paint)
    }
}
