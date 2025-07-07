package io.cobrowse.sample

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
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
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
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
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    private fun cleanupVoiceChat() {
        try {
            VoiceChatIframeController.getInstance().destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up voice chat", e)
        }
    }
}