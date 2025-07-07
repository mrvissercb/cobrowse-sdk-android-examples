package io.cobrowse.sample

import android.app.Activity
import android.app.Application
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.content.Context
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import io.cobrowse.CobrowseIO
import io.cobrowse.sample.data.CobrowseSessionDelegate
import io.cobrowse.sample.data.getAndroidLogTag
import io.cobrowse.sample.ui.VoiceChatIframeController

/**
 * Android application class.
 */
class MainApplication : Application(), Application.ActivityLifecycleCallbacks {

    @Suppress("PrivatePropertyName")
    private val Any.TAG: String
        get() = javaClass.getAndroidLogTag()

    private var activityCount = 0
    private var virtualAgentWidget: FloatingActionButton? = null

    override fun onCreate() {
        super.onCreate()
        
        // Register for activity lifecycle callbacks
        registerActivityLifecycleCallbacks(this)

        System.out.println("Ok going to initialize...")
        with(CobrowseIO.instance()) {
            api("https://cobrowse-branden.ngrok.dev")
            license("85jA6dDyfO6a2w")
            customData(buildMap<String, String> {
                put(CobrowseIO.USER_EMAIL_KEY, "android@demo.com")
                put(CobrowseIO.DEVICE_NAME_KEY, "Android Demo")
            })
            webviewRedactedViews(arrayOf(
                "#title",
                "#amount",
                "#subtitle",
                "#map"
            ))
            setDelegate(CobrowseSessionDelegate.getInstance())

            System.out.println("starting...")
            start()
        }

        // Initialize voice chat iframe controller
        Log.d(TAG, "Initializing VoiceChatIframeController from MainApplication")
        VoiceChatIframeController.getInstance().initialize(this)

        // If using Firebase Messaging to start sessions please include your own `google-services.json`
        if (FirebaseApp.getApps(this).size > 0) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@OnCompleteListener
                }
                CobrowseIO.instance().setDeviceToken(task.result)
            })
        } else {
            Log.w(TAG, "Firebase app is not initialized. Did you copy your `google-services.json` file?")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // Clean up voice chat when app is terminated (rarely called in production)
        cleanupVoiceChat()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        activityCount++
    }
    override fun onActivityResumed(activity: Activity) {
        // Ensure permissions are requested for voice chat
        VoiceChatIframeController.getInstance().requestPermissionsIfNeeded(activity)
        
        Log.d(TAG, "onActivityResumed: ${activity.javaClass.simpleName}, widget exists: ${virtualAgentWidget != null}")
        // Always remove any existing widget and create a new one for this activity
        removeVirtualAgentWidget()
        addVirtualAgentWidget(activity)
    }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityStopped(activity: Activity) {
        activityCount--
        if (activityCount == 0) {
            // All activities stopped - app is going to background
            Log.d(TAG, "App going to background - completely destroying voice chat")
            try {
                // More aggressive cleanup when app goes to background
                VoiceChatIframeController.getInstance().destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying voice chat", e)
            }
        }
        
        // Only remove widget if it belongs to this activity
        Log.d(TAG, "onActivityStopped: ${activity.javaClass.simpleName}")
        virtualAgentWidget?.let { widget ->
            if (widget.context == activity) {
                Log.d(TAG, "Widget belongs to stopped activity, removing it")
                removeVirtualAgentWidget()
            } else {
                Log.d(TAG, "Widget belongs to different activity, keeping it")
            }
        }
    }
    
    override fun onActivityDestroyed(activity: Activity) {
        Log.d(TAG, "onActivityDestroyed: ${activity.javaClass.simpleName}")
    }

    private fun cleanupVoiceChat() {
        try {
            VoiceChatIframeController.getInstance().destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up voice chat", e)
        }
    }

    private fun addVirtualAgentWidget(activity: Activity) {
        try {
            virtualAgentWidget = FloatingActionButton(activity).apply {
                setImageResource(R.drawable.ic_cobrowse_favicon)
                imageTintList = null  // Disable automatic tinting
                backgroundTintList = null  // Remove FAB background color
                setOnClickListener { 
                    Log.d(TAG, "Virtual agent widget clicked")
                    VoiceChatIframeController.getInstance().toggleSession()
                }
                scaleX = 0.8f
                scaleY = 0.8f
            }
            
            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = WindowManager.LayoutParams.TYPE_APPLICATION
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.BOTTOM or Gravity.END
                // Position in bottom right with margins
                x = dpToPx(16)
                y = dpToPx(16)
            }
            
            activity.windowManager.addView(virtualAgentWidget, params)
            Log.d(TAG, "Virtual agent widget added")
        } catch (e: Exception) {
            Log.w(TAG, "Error adding virtual agent widget", e)
        }
    }

    private fun removeVirtualAgentWidget() {
        virtualAgentWidget?.let { widget ->
            try {
                Log.d(TAG, "Removing virtual agent widget - context: ${widget.context}")
                // Get the window manager from the widget's context
                val windowManager = widget.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.removeView(widget)
                Log.d(TAG, "Successfully removed virtual agent widget")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing virtual agent widget: ${e.message}", e)
            } finally {
                virtualAgentWidget = null
                Log.d(TAG, "Cleared widget reference")
            }
        } ?: Log.d(TAG, "No widget to remove")
    }

    // Convert dp to px because WindowManager positioning requires actual pixels,
    // while dp provides consistent sizing across different screen densities
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}