package com.drdisagree.xmorphclock.ui.components

import android.graphics.Bitmap
import android.graphics.RectF
import com.drdisagree.xmorphclock.domain.model.ClockGeometry
import kotlin.math.max

fun backdropFrame(bitmap: Bitmap, width: Float, height: Float, geometry: ClockGeometry, largeProgress: Float): RectF =
    backdropFrameInto(bitmap, width, height, geometry, largeProgress, RectF())

fun backdropFrameInto(
    bitmap: Bitmap,
    width: Float,
    height: Float,
    geometry: ClockGeometry,
    largeProgress: Float,
    out: RectF
): RectF {
    val frame = geometry.frame(largeProgress)
    val scale = max(width / bitmap.width, height / bitmap.height)
    val w = bitmap.width * scale
    val h = bitmap.height * scale
    val left = (width - w) / 2f - frame.left * width
    val top = (height - h) / 2f - frame.top * height
    out.set(left, top, left + w, top + h)
    return out
}
