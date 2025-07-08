package io.cobrowse.sample.ui

import android.app.Activity
import android.util.Log
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
     * Send a contextual update to the virtual agent
     * Package-private for use by ViewHierarchyWatcher
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