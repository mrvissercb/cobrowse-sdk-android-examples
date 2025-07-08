package io.cobrowse.sample.ui

import android.app.Activity
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import io.cobrowse.sample.data.getAndroidLogTag

/**
 * Singleton class that manages global UI interaction monitoring across activities.
 * Tracks user interactions and sends contextual updates to the virtual agent.
 */
class InteractionTracker private constructor() {
    
    @Suppress("PrivatePropertyName")
    private val Any.TAG: String
        get() = javaClass.getAndroidLogTag()

    companion object {
        @Volatile
        private var INSTANCE: InteractionTracker? = null

        fun getInstance(): InteractionTracker {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InteractionTracker().also { INSTANCE = it }
            }
        }
    }

    private val activityWatchers = mutableMapOf<Activity, ActivityInteractionWatcher>()

    /**
     * Start monitoring UI interactions for the given activity
     */
    fun startMonitoring(activity: Activity) {
        Log.d(TAG, "Starting interaction monitoring for ${activity.javaClass.simpleName}")
        
        // Remove any existing watcher for this activity
        stopMonitoring(activity)
        
        val watcher = ActivityInteractionWatcher(activity)
        activityWatchers[activity] = watcher
        watcher.attachGlobalListeners()
        
        Log.d(TAG, "Interaction monitoring started for ${activity.javaClass.simpleName}")
    }

    /**
     * Stop monitoring UI interactions for the given activity
     */
    fun stopMonitoring(activity: Activity) {
        val watcher = activityWatchers.remove(activity)
        if (watcher != null) {
            Log.d(TAG, "Stopping interaction monitoring for ${activity.javaClass.simpleName}")
            watcher.detachGlobalListeners()
        }
    }

    /**
     * Force cleanup all monitoring - used for crash recovery scenarios
     */
    fun cleanupAll() {
        Log.d(TAG, "Force cleanup all interaction monitoring")
        activityWatchers.values.forEach { watcher ->
            try {
                watcher.detachGlobalListeners()
            } catch (e: Exception) {
                Log.w(TAG, "Error during force cleanup", e)
            }
        }
        activityWatchers.clear()
    }

    /**
     * Handle touch event for click detection - to be called from Activity.dispatchTouchEvent
     */
    fun handleTouchEvent(event: MotionEvent, activity: Activity): Boolean {
        // Only process ACTION_UP events for click detection
        if (event.action != MotionEvent.ACTION_UP) {
            return false
        }
        
        // Find the view at the touch coordinates
        val rootView = activity.findViewById<View>(android.R.id.content)
        val touchedView = findViewAt(rootView, event.x, event.y)
        
        if (touchedView != null && isClickableView(touchedView)) {
            handleViewClick(touchedView)
        }
        
        return false // Don't consume the event, let normal processing continue
    }

    /**
     * Find the view at the given coordinates
     */
    private fun findViewAt(view: View, x: Float, y: Float): View? {
        if (!view.isShown || !isPointInView(view, x, y)) {
            return null
        }
        
        // If this is a ViewGroup, check children first (deepest first)
        if (view is android.view.ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                val foundView = findViewAt(child, x, y)
                if (foundView != null) {
                    return foundView
                }
            }
        }
        
        // Return this view if no children match
        return view
    }

    /**
     * Check if a point is within a view's bounds
     */
    private fun isPointInView(view: View, x: Float, y: Float): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val viewX = location[0]
        val viewY = location[1]
        
        return x >= viewX && x <= viewX + view.width &&
               y >= viewY && y <= viewY + view.height
    }

    /**
     * Check if a view is clickable (Button, ImageButton, or has click listener)
     * Excludes EditText since we handle those via focus change events
     */
    private fun isClickableView(view: View): Boolean {
        // Exclude EditText - we handle those via focus change events
        if (view is android.widget.EditText) {
            return false
        }
        
        return view is Button || 
               view is ImageButton || 
               (view.isClickable && view.hasOnClickListeners())
    }

    /**
     * Handle click on a view
     */
    private fun handleViewClick(view: View) {
        val contentDesc = view.contentDescription?.toString() ?: getViewDescription(view)
        val message = "User tapped '$contentDesc'"
        
        Log.d(TAG, "Click detected: $message")
        sendContextualUpdate(message)
    }

    /**
     * Get a description for a view when contentDescription is not available
     */
    private fun getViewDescription(view: View): String {
        return when (view) {
            is Button -> "button"
            is ImageButton -> "image button"
            else -> "clickable element"
        }
    }

    /**
     * Send a contextual update to the virtual agent
     * Package-private for use by ActivityInteractionWatcher
     */
    internal fun sendContextualUpdate(message: String) {
        Log.d(TAG, "Sending contextual update: $message")
        try {
            VoiceChatIframeController.getInstance().sendContextualUpdate(message)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send contextual update: $message", e)
        }
    }
}