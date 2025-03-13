package com.nexell.mobiledet.data

import android.graphics.RectF

data class DetectionResult(
    var score: Pair<String, Float>,
    val boundingBox: RectF? = null
) {
    fun requireBoundingBox(): RectF {
        return boundingBox!!
    }
}