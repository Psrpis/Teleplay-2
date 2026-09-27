package com.telegramtv.ui.mobile.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

fun Modifier.horizontalPageSwipe(
    enabled: Boolean = true,
    threshold: Float = 72f,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {}
): Modifier = if (!enabled) this else pointerInput(threshold, onSwipeLeft, onSwipeRight) {
    var totalX = 0f
    var totalY = 0f
    detectDragGestures(
        onDragStart = {
            totalX = 0f
            totalY = 0f
        },
        onDragCancel = {
            totalX = 0f
            totalY = 0f
        },
        onDragEnd = {
            if (abs(totalX) >= threshold && abs(totalX) > abs(totalY) * 1.25f) {
                if (totalX < 0) onSwipeLeft() else onSwipeRight()
            }
            totalX = 0f
            totalY = 0f
        },
        onDrag = { change, dragAmount ->
            totalX += dragAmount.x
            totalY += dragAmount.y
            if (abs(totalX) > abs(totalY) && abs(totalX) > 12f) change.consumePositionChange()
        }
    )
}
