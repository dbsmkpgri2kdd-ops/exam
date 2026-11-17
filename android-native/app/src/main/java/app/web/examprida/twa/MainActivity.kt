package app.web.examprida.twa

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {

    private lateinit var rootContainer: View
    private lateinit var webView: WebView
    private lateinit var bannerContainer: FrameLayout
    private lateinit var nativeAdContainer: FrameLayout
    private lateinit var adMobManager: AdMobManager
    private lateinit var assetLoader: WebViewAssetLoader

    @Volatile
    private var isExamLocked = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        evaluateWebCallback("window.onAndroidCameraPermissionResult && window.onAndroidCameraPermissionResult($granted);")
    }

    private val googleSyncLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        evaluateWebCallback("if (typeof evaluateChecklistStates === 'function') evaluateChecklistStates();")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootContainer = findViewById(android.R.id.content)
        webView = findViewById(R.id.examWebView)
        bannerContainer = findViewById(R.id.bannerAdContainer)
        nativeAdContainer = findViewById(R.id.nativeAdContainer)

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            if (isExamLocked) {
                // Saat ujian terkunci: immersive penuh, namun tetap beri ruang keyboard saat mengetik
                val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                view.setPadding(0, 0, 0, imeBottom)
            } else {
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            }
            insets
        }

        adMobManager = AdMobManager(this)
        adMobManager.hideAllAdsImmediately(bannerContainer, nativeAdContainer)
        if (!AdMobConfig.isExamInProgress(this)) {
            adMobManager.preloadAll()
        }

        setupWebView()
        setupBackInterceptor()
        applyImmersiveMode(false)

        val targetUrl = intent?.getStringExtra("override_url") ?: BuildConfig.EXAM_START_URL
        webView.loadUrl(targetUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString.replace("; wv", "")
        }

        webView.addJavascriptInterface(AndroidAppBridge(), "AndroidApp")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url ?: return null
                return assetLoader.shouldInterceptRequest(url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("examprida://lock")) {
                    val lock = Uri.parse(url).getQueryParameter("state") == "true"
                    setNativeExamLock(lock)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                runOnUiThread {
                    val resources = request.resources
                    val needsCamera = resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                    if (needsCamera && !hasCameraPermissionInternal()) {
                        request.deny()
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        request.grant(resources)
                    }
                }
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("PRIDA EXAMBRO")
                    .setMessage(message ?: "")
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setCancelable(false)
                    .show()
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Konfirmasi")
                    .setMessage(message ?: "")
                    .setPositiveButton("Ya") { _, _ -> result?.confirm() }
                    .setNegativeButton("Batal") { _, _ -> result?.cancel() }
                    .setCancelable(false)
                    .show()
                return true
            }
        }
    }

    private fun setupBackInterceptor() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isExamLocked) {
                    evaluateWebCallback("if (typeof reportSecurityViolationOnce === 'function') reportSecurityViolationOnce('native-back', 'Menekan Tombol Kembali Saat Ujian', 3000);")
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isExamLocked && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            return super.onKeyDown(keyCode, event)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (isExamLocked && isInMultiWindowMode) {
            evaluateWebCallback("if (typeof reportSecurityViolationOnce === 'function') reportSecurityViolationOnce('multi-window', 'Mode Split Screen Android Terdeteksi', 4000);")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isExamLocked) {
            applyImmersiveMode(true)
        }
    }

    private fun setNativeExamLock(lock: Boolean) {
        runOnUiThread {
            isExamLocked = lock
            adMobManager.isExamLocked = lock
            AdMobConfig.setExamInProgress(this, lock)
            (application as? ExambroApplication)?.isExamActiveGlobally = lock

            if (lock) {
                adMobManager.isExamPreparing = false
                (application as? ExambroApplication)?.isExamPreparingGlobally = false
                adMobManager.hideAllAdsImmediately(bannerContainer, nativeAdContainer)
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                applyImmersiveMode(true)
                ViewCompat.requestApplyInsets(rootContainer)
                try {
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                        startLockTask()
                    }
                } catch (_: Exception) {
                }
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                applyImmersiveMode(false)
                ViewCompat.requestApplyInsets(rootContainer)
                try {
                    stopLockTask()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun applyImmersiveMode(enable: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (enable) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun hasCameraPermissionInternal(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun evaluateWebCallback(js: String) {
        runOnUiThread {
            webView.evaluateJavascript(js, null)
        }
    }

    override fun onDestroy() {
        adMobManager.destroy()
        webView.destroy()
        super.onDestroy()
    }

    inner class AndroidAppBridge {

        @JavascriptInterface
        fun isNativeApp(): Boolean = true

        @JavascriptInterface
        fun getAppVersion(): String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        @JavascriptInterface
        fun setExamLock(lock: Boolean) {
            setNativeExamLock(lock)
        }

        @JavascriptInterface
        fun setExamPreparing(preparing: Boolean) {
            runOnUiThread {
                adMobManager.isExamPreparing = preparing
                (application as? ExambroApplication)?.isExamPreparingGlobally = preparing
                if (preparing) {
                    adMobManager.hideAllAdsImmediately(bannerContainer, nativeAdContainer)
                }
            }
        }

        @JavascriptInterface
        fun hasCameraPermission(): Boolean = hasCameraPermissionInternal()

        @JavascriptInterface
        fun requestCameraPermissionFromWeb() {
            runOnUiThread {
                if (hasCameraPermissionInternal()) {
                    evaluateWebCallback("window.onAndroidCameraPermissionResult && window.onAndroidCameraPermissionResult(true);")
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }

        @JavascriptInterface
        fun hasGoogleAccountCookie(): Boolean {
            val cookie = CookieManager.getInstance().getCookie("https://accounts.google.com") ?: ""
            return cookie.contains("SID=") || cookie.contains("SSID=") || cookie.contains("LSID=")
        }

        @JavascriptInterface
        fun openGoogleAccountSync() {
            runOnUiThread {
                adMobManager.isExamPreparing = true
                (application as? ExambroApplication)?.isExamPreparingGlobally = true
                adMobManager.hideAllAdsImmediately(bannerContainer, nativeAdContainer)
                googleSyncLauncher.launch(Intent(this@MainActivity, GoogleSyncActivity::class.java))
            }
        }

        @JavascriptInterface
        fun showBannerAd(show: Boolean) {
            adMobManager.loadAndToggleBanner(bannerContainer, show)
        }

        @JavascriptInterface
        fun showNativeAd(show: Boolean) {
            adMobManager.toggleNativeAd(nativeAdContainer, show)
        }

        @JavascriptInterface
        fun showInterstitialAd() {
            adMobManager.showInterstitial {
                evaluateWebCallback("window.onAndroidAdClosed && window.onAndroidAdClosed();")
            }
        }

        @JavascriptInterface
        fun showRewardedInterstitialAd() {
            adMobManager.showRewardedInterstitial { earned ->
                evaluateWebCallback("window.onAndroidRewardedInterstitialResult && window.onAndroidRewardedInterstitialResult($earned);")
            }
        }

        @JavascriptInterface
        fun showRewardedAd() {
            adMobManager.showRewarded { earned ->
                evaluateWebCallback("window.onAndroidRewardedResult && window.onAndroidRewardedResult($earned);")
            }
        }

        @JavascriptInterface
        fun updateAdMobSettings(jsonConfig: String) {
            AdMobConfig.saveDynamicSettings(this@MainActivity, jsonConfig)
            runOnUiThread {
                adMobManager.resetCachedAdsForSettingsChange()
                if (!isExamLocked && !adMobManager.isExamPreparing && !AdMobConfig.isOwnerAdSafeMode(this@MainActivity)) {
                    adMobManager.preloadAll()
                }
            }
        }

        @JavascriptInterface
        fun setOwnerAdSafeMode(enabled: Boolean) {
            AdMobConfig.setOwnerAdSafeMode(this@MainActivity, enabled)
            runOnUiThread {
                if (enabled) {
                    adMobManager.hideAllAdsImmediately(bannerContainer, nativeAdContainer)
                } else if (!isExamLocked && !adMobManager.isExamPreparing) {
                    adMobManager.preloadAll()
                }
            }
        }

        @JavascriptInterface
        fun isOwnerAdSafeMode(): Boolean = AdMobConfig.isOwnerAdSafeMode(this@MainActivity)

        @JavascriptInterface
        fun getAdmobTestDeviceHash(): String {
            val cfg = AdMobConfig.getDynamicSettings(this@MainActivity)
            return AdMobConfig.getTestDeviceHashedId(cfg.testDeviceId)
        }

        @JavascriptInterface
        fun openExternalUrl(url: String) {
            if (isExamLocked) return
            runOnUiThread {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                }
            }
        }
    }
}
