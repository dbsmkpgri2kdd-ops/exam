package app.web.examprida.twa

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class GoogleSyncActivity : AppCompatActivity() {

    private lateinit var syncWebView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_google_sync)

        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        syncWebView = findViewById(R.id.googleSyncWebView)
        val btnClose = findViewById<Button>(R.id.btnCloseSync)
        btnClose.setOnClickListener {
            CookieManager.getInstance().flush()
            setResult(RESULT_OK)
            finish()
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(syncWebView, true)

        syncWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = userAgentString.replace("; wv", "")
        }

        syncWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
                if (url != null && (url.contains("myaccount.google.com") || url.contains("accounts.google.com/SignOutOptions"))) {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }

        syncWebView.loadUrl("https://accounts.google.com/ServiceLogin?continue=https://docs.google.com/forms/")
    }

    override fun onDestroy() {
        CookieManager.getInstance().flush()
        syncWebView.destroy()
        super.onDestroy()
    }
}
