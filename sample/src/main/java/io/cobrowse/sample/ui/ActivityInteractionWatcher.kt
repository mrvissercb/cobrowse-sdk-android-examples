package io.cobrowse.sample.ui

import android.app.Activity
import android.util.Log
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import io.cobrowse.sample.data.getAndroidLogTag

/**
 * Monitors interactions within a specific activity using global event listeners.
 * Uses ViewTreeObserver for efficient global event monitoring without view hierarchy traversal.
 */
class ActivityInteractionWatcher(private val activity: Activity) {

    @Suppress("PrivatePropertyName")
    private val Any.TAG: String
        get() = javaClass.getAndroidLogTag()

    private var globalFocusChangeListener: ViewTreeObserver.OnGlobalFocusChangeListener? = null
    private var viewTreeObserver: ViewTreeObserver? = null
    
    // Store initial text values for EditText focus tracking
    private val editTextInitialValues = mutableMapOf<EditText, String>()

    /**
     * Attach global event listeners to monitor interactions in this activity
     */
    fun attachGlobalListeners() {
        Log.d(TAG, "Attaching global listeners for ${activity.javaClass.simpleName}")
        
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        if (rootView == null) {
            Log.w(TAG, "Root view not found")
            return
        }

        viewTreeObserver = rootView.viewTreeObserver
        
        // Create and attach global focus change listener
        globalFocusChangeListener = ViewTreeObserver.OnGlobalFocusChangeListener { oldFocus, newFocus ->
            handleGlobalFocusChange(oldFocus, newFocus)
        }
        
        viewTreeObserver?.addOnGlobalFocusChangeListener(globalFocusChangeListener)
        Log.d(TAG, "Global focus change listener attached")
    }

    /**
     * Cleanup internal state - Android handles ViewTreeObserver listener cleanup automatically
     */
    fun detachGlobalListeners() {
        Log.d(TAG, "Cleaning up internal state - Android will handle ViewTreeObserver cleanup")
        
        editTextInitialValues.clear()
        globalFocusChangeListener = null
        viewTreeObserver = null
        
        Log.d(TAG, "Internal state cleared")
    }

    /**
     * Handle global focus changes across all views in the activity
     */
    private fun handleGlobalFocusChange(oldFocus: android.view.View?, newFocus: android.view.View?) {
        // Handle EditText focus lost
        if (oldFocus is EditText) {
            handleEditTextFocusLost(oldFocus)
        }
        
        // Handle EditText focus gained
        if (newFocus is EditText) {
            handleEditTextFocusGained(newFocus)
        }
    }

    /**
     * Handle when an EditText gains focus - store initial value
     */
    private fun handleEditTextFocusGained(editText: EditText) {
        val initialValue = editText.text.toString()
        editTextInitialValues[editText] = initialValue
        
        Log.d(TAG, "EditText focused, initial value stored for: ${editText.contentDescription}")
    }

    /**
     * Handle when an EditText loses focus - check for changes and send update
     */
    private fun handleEditTextFocusLost(editText: EditText) {
        val initialValue = editTextInitialValues.remove(editText)
        val currentValue = editText.text.toString()
        
        if (initialValue != null && initialValue != currentValue) {
            val contentDesc = editText.contentDescription?.toString() ?: "text field"
            val message = if (initialValue.isEmpty()) {
                "User entered '$currentValue' in '$contentDesc' field"
            } else {
                "User changed '$contentDesc' from '$initialValue' to '$currentValue'"
            }
            
            Log.d(TAG, "Text change detected: $message")
            InteractionTracker.getInstance().sendContextualUpdate(message)
        }
    }
}