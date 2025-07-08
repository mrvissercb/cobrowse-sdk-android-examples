package io.cobrowse.sample.ui

import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import io.cobrowse.sample.data.getAndroidLogTag

/**
 * Base activity that provides global UI interaction tracking for all activities.
 * All activities should extend this to automatically enable click and text change tracking
 * for the virtual agent contextual updates.
 */
abstract class InteractionTrackingActivity : AppCompatActivity() {

    @Suppress("PrivatePropertyName")
    private val Any.TAG: String
        get() = javaClass.getAndroidLogTag()

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let { event ->
            Log.d(TAG, "dispatchTouchEvent: action=${getActionName(event.action)}, x=${event.x}, y=${event.y}")
            
            // Let InteractionTracker handle button click detection
            InteractionTracker.getInstance().handleTouchEvent(event, this)
        }
        
        // Continue with normal touch event processing
        return super.dispatchTouchEvent(ev)
    }
    
    private fun getActionName(action: Int): String {
        return when (action) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            else -> "UNKNOWN($action)"
        }
    }
}