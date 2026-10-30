package com.vincenthzr.locationspoofer.ui.components.map

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.hypot

/** Observes drags without intercepting map gestures or SDK child views. */
class MapInteractionContainer(context: Context) : FrameLayout(context) {
    var onUserDrag: (() -> Unit)? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var notified = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX = event.x; startY = event.y; notified = false }
            MotionEvent.ACTION_MOVE -> if (!notified && hypot(event.x - startX, event.y - startY) > touchSlop) {
                notified = true
                onUserDrag?.invoke()
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
