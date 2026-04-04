package com.webscare.orangelinelahore.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.annotation.DrawableRes
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import androidx.core.graphics.createBitmap
import com.webscare.orangelinelahore.R

object MapUtils {

    fun bitmapDescriptorFromVector(
        context: Context,
        drawableId: Int
    ): BitmapDescriptor {

        val drawable = ContextCompat.getDrawable(context, drawableId)
            ?: throw IllegalArgumentException("Drawable not found")

        drawable.setBounds(
            0,
            0,
            drawable.intrinsicWidth,
            drawable.intrinsicHeight
        )

        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)

        val canvas = Canvas(bitmap)
        drawable.draw(canvas)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    fun iconTextLabel(
        context: Context,
        @DrawableRes iconRes: Int,
        text: String,
        iconColor: Int,                 // appColor (ONLY for drawable)
        @FontRes fontRes: Int,           // font from res/font
        textSizeSp: Float = 16f,
        gapDp: Float = 6f
    ): BitmapDescriptor {

        val dm = context.resources.displayMetrics
        val density = dm.density
        val scaledDensity = dm.scaledDensity

        // ---- TEXT PAINT ----
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.black)
            textSize = textSizeSp * scaledDensity
            typeface = ResourcesCompat.getFont(context, fontRes)
            setShadowLayer(
                1.5f * density,
                0f,
                1f * density,
                Color.argb(70, 0, 0, 0)
            )
        }

        val textWidth = textPaint.measureText(text)
        val fm = textPaint.fontMetrics
        val textHeight = (fm.bottom - fm.top)

        val gapPx = (gapDp * density).toInt()

        // ---- ICON ----
        val dr = ContextCompat.getDrawable(context, iconRes)
        val iconSizePx = textHeight.toInt() // ✅ icon stretches to text height

        val width = (iconSizePx + gapPx + textWidth).toInt().coerceAtLeast(1)
        val height = (textHeight + 2f * density).toInt().coerceAtLeast(1)

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        // ---- DRAW ICON ----
        dr?.let {
            it.setTint(iconColor)
            val top = ((height - iconSizePx) / 2f).toInt()
            it.setBounds(0, top, iconSizePx, top + iconSizePx)
            it.draw(canvas)
        }

        // ---- DRAW TEXT ----
        val xText = (iconSizePx + gapPx).toFloat()
        val yText = (height / 2f) - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, xText, yText, textPaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

}