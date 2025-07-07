package io.cobrowse.sample.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.cobrowse.CobrowseIO
import org.json.JSONException
import org.json.JSONObject

/**
 * Singleton manager that controls the voice chat iframe integration with Cobrowse virtual agent
 */
class VoiceChatIframeController private constructor() {

    private val vaUrl = "https://b91fd8994b7d.ngrok.app"

    private var webView: WebView? = null
    private var isReady = false
    private var isInitialized = false

    companion object {
        private const val TAG = "VoiceChatIframe"
        private const val REQUEST_CODE_PERMISSIONS = 11
        
        @Volatile
        private var INSTANCE: VoiceChatIframeController? = null

        fun getInstance(): VoiceChatIframeController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceChatIframeController().also { INSTANCE = it }
            }
        }
    }

    fun initialize(context: Context) {
        if (webView != null) {
            Log.d(TAG, "VoiceChatIframeController already initialized")
            return
        }
        
        Log.d(TAG, "Initializing VoiceChatIframeController")
        webView = WebView(context.applicationContext)
        setupWebView()
        loadIframe()
    }
    
    fun requestPermissionsIfNeeded(activity: Activity): Boolean {
        val permissionCheck = ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Requesting microphone permission")
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS),
                REQUEST_CODE_PERMISSIONS
            )
            return false
        }
        Log.d(TAG, "Microphone permission already granted")
        return true
    }

    private fun setupWebView() {
        val webView = requireNotNull(webView) { "WebView must be initialized before setup" }
        
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.domStorageEnabled = true
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        
        // Additional settings for audio/media support
        webSettings.allowFileAccessFromFileURLs = true
        webSettings.allowUniversalAccessFromFileURLs = true
        webSettings.databaseEnabled = true

        // Enable microphone permissions
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                Log.d(TAG, "WebView permission request: ${request.resources.contentToString()}")
                Log.d(TAG, "Request origin: ${request.origin}")
                
                // Check if the request includes microphone permission
                val microphoneRequested = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                if (microphoneRequested) {
                    Log.d(TAG, "Granting microphone permission to WebView")
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                } else {
                    Log.d(TAG, "Granting all requested permissions: ${request.resources.contentToString()}")
                    request.grant(request.resources)
                }
            }
            
            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                Log.w(TAG, "WebView permission request canceled: ${request.resources.contentToString()}")
            }
        }

        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(WebAppInterface(), "Android")
    }

    private fun loadIframe() {
        val webView = requireNotNull(webView) { "WebView must be initialized before loading iframe" }
        
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name='viewport' content='width=device-width, initial-scale=1'>
                <style>
                    body { margin: 0; padding: 0; }
                    iframe { width: 100%; height: 100vh; border: none; }
                </style>
            </head>
            <body>
                <iframe id='xi-iframe' src='$vaUrl/iframe' 
                        allow='microphone' frameborder='0'></iframe>
                <script>
                    window.addEventListener('message', function(event) {
                        Android.onMessage(JSON.stringify(event.data));
                    });
                    
                    function sendMessage(message) {
                        try {
                            const iframe = document.getElementById('xi-iframe');
                            if (iframe && iframe.contentWindow) {
                                iframe.contentWindow.postMessage(JSON.parse(message), '*');
                            }
                        } catch (e) {
                            console.error('Error sending message:', e);
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(vaUrl, html, "text/html", "UTF-8", null)
    }

    private inner class WebAppInterface {
        @JavascriptInterface
        fun onMessage(message: String) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    val msg = JSONObject(message)
                    val type = msg.getString("type")

                    when (type) {
                        "ready" -> {
                            Log.d(TAG, "Iframe ready signal received")
                            isReady = true
                            initializeConversation()
                        }
                        "statusUpdate" -> {
                            Log.d(TAG, "Status update received: $msg")
                            handleStatusUpdate(msg.getJSONObject("payload"))
                        }
                        "error" -> {
                            Log.e(TAG, "Error received: $msg")
                            handleError(msg.getJSONObject("payload"))
                        }
                    }
                } catch (e: JSONException) {
                    Log.e(TAG, "Error parsing message", e)
                }
            }
        }
    }

    private fun initializeConversation() {
        if (!isReady || isInitialized) {
            Log.d(TAG, "Not ready to initialize: isReady=$isReady, isInitialized=$isInitialized")
            return
        }

        val webView = requireNotNull(webView) { "WebView not initialized" }

        // Get device ID from CobrowseIO instance
        val deviceId = CobrowseIO.instance().deviceId()
        if (deviceId.isNullOrEmpty()) {
            Log.d(TAG, "CobrowseIO device ID not available yet, retrying in 1 second")
            // CobrowseIO not ready yet, retry in a moment
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                initializeConversation()
            }, 1000)
            return
        }

        Log.d(TAG, "Initializing conversation with device ID: $deviceId")

        try {
            val initPayload = JSONObject().apply {
                put("agentProfile", JSONObject().apply {
                    put("agentId", "agent_01jxx23q95es3a1y7amhf0jbc4")
                    put("cobrowseAvailable", true)
                })
                put("apiBaseUrl", vaUrl)
                put("deviceId", deviceId)
                put("micMuted", false)
                put("url", "https://financetracker.com")
            }

            val initMessage = JSONObject().apply {
                put("type", "init")
                put("payload", initPayload)
            }

            Log.d(TAG, "Sending init message: $initMessage")
            webView.evaluateJavascript("sendMessage('${initMessage.toString().replace("'", "\\'")}')", null)

            // Start session immediately
            val startMessage = JSONObject().apply {
                put("type", "startSession")
            }

            Log.d(TAG, "Sending start session message")
            webView.evaluateJavascript("sendMessage('${startMessage.toString().replace("'", "\\'")}')", null)
            
            isInitialized = true
            Log.d(TAG, "Voice chat initialization completed")
        } catch (e: JSONException) {
            Log.e(TAG, "Error initializing conversation", e)
        }
    }

    private fun handleStatusUpdate(payload: JSONObject) {
        try {
            val status = payload.getJSONObject("status")
            val statusName = status.getString("name")
            val isSpeaking = payload.getBoolean("isSpeaking")
            
            Log.d(TAG, "Status update - Status: $statusName, Speaking: $isSpeaking")
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing status update", e)
        }
    }

    private fun handleError(payload: JSONObject) {
        try {
            val error = payload.getString("error")
            Log.e(TAG, "Voice chat error: $error")
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing error message", e)
        }
    }

    fun sendContextualUpdate(text: String) {
        val webView = requireNotNull(webView) { "WebView not initialized" }
        try {
            val message = JSONObject().apply {
                put("type", "sendContextualUpdate")
                put("payload", JSONObject().put("text", text))
            }

            webView.evaluateJavascript("sendMessage('${message.toString().replace("'", "\\'")}')", null)
        } catch (e: JSONException) {
            Log.e(TAG, "Error sending contextual update", e)
        }
    }

    fun endSession() {
        val webView = requireNotNull(webView) { "WebView not initialized" }
        try {
            val message = JSONObject().apply {
                put("type", "endSession")
            }

            webView.evaluateJavascript("sendMessage('${message.toString().replace("'", "\\'")}')", null)
        } catch (e: JSONException) {
            Log.e(TAG, "Error ending session", e)
        }
    }

    fun destroy() {
        Log.d(TAG, "Destroying VoiceChatIframeController")
        try {
            // First try to end session
            endSession()
            
            // Clear the WebView
            webView?.let { webView ->
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.clearCache(true)
                webView.clearFormData()
                webView.destroy()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        } finally {
            webView = null
            isReady = false
            isInitialized = false
            INSTANCE = null
        }
    }
}