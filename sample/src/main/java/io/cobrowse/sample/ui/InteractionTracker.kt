package io.cobrowse.sample.ui

import android.app.Activity
import android.content.SharedPreferences
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.preference.PreferenceManager
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
    private var preferencesListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var isPreferenceListenerRegistered = false

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
        
        // Setup global preference listener on first activity
        setupGlobalPreferenceListener(activity)
        
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
        
        // Clean up preference listener
        cleanupGlobalPreferenceListener()
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
        val clickableView = touchedView?.let { findClickableViewInHierarchy(it) }
        
        if (clickableView != null) {
            handleViewClick(clickableView)
        }
        
        return false // Don't consume the event, let normal processing continue
    }

    /**
     * Find the first clickable view in the parent hierarchy starting from the given view
     */
    private fun findClickableViewInHierarchy(view: View): View? {
        var currentView: View? = view
        
        while (currentView != null) {
            if (isClickableView(currentView)) {
                return currentView
            }
            currentView = currentView.parent as? View
        }
        
        return null
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
        
        // Check for explicitly clickable views
        if (view is Button || view is ImageButton) {
            return true
        }
        
        // For other views, check if they have click listeners
        // Note: We check hasOnClickListeners first because some views (like FrameLayout)
        // might have listeners but not report as clickable immediately
        if (view.hasOnClickListeners()) {
            return true
        }
        
        // Fallback to checking if view is clickable (covers views with clickable="true" in XML)
        return view.isClickable
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
     * Setup global preference change listener
     */
    private fun setupGlobalPreferenceListener(activity: Activity) {
        if (isPreferenceListenerRegistered) {
            return // Already registered
        }
        
        try {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            
            preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                handlePreferenceChange(prefs, key)
            }
            
            sharedPreferences.registerOnSharedPreferenceChangeListener(preferencesListener)
            isPreferenceListenerRegistered = true
            
            Log.d(TAG, "Global preference listener registered")
        } catch (e: Exception) {
            Log.w(TAG, "Error setting up global preference listener", e)
        }
    }

    /**
     * Clean up global preference listener
     */
    private fun cleanupGlobalPreferenceListener() {
        preferencesListener?.let { listener ->
            try {
                // We need an activity context to get SharedPreferences for cleanup
                activityWatchers.keys.firstOrNull()?.let { activity ->
                    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
                    sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
                    Log.d(TAG, "Global preference listener unregistered")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up global preference listener", e)
            }
        }
        
        preferencesListener = null
        isPreferenceListenerRegistered = false
    }

    /**
     * Handle preference changes and send contextual updates
     */
    private fun handlePreferenceChange(prefs: SharedPreferences, key: String?) {
        if (key == null) return
        
        try {
            val value = prefs.all[key]
            val message = when (value) {
                is Boolean -> "User ${if (value) "enabled" else "disabled"} preference '$key'"
                is String -> "User changed preference '$key' to '$value'"
                is Int, is Long, is Float -> "User changed preference '$key' to '$value'"
                else -> "User changed preference '$key'"
            }
            
            Log.d(TAG, "Preference change detected: $message")
            sendContextualUpdate(message)
        } catch (e: Exception) {
            Log.w(TAG, "Error handling preference change for key: $key", e)
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