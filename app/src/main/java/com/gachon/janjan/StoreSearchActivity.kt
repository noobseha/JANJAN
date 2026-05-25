package com.gachon.janjan

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class StoreSearchActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.settings.allowFileAccessFromFileURLs = true
        webView.settings.allowUniversalAccessFromFileURLs = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(StoreInterface(), "Android")
        webView.loadUrl("file:///android_asset/kakao_store_search.html")
    }

    inner class StoreInterface {
        @JavascriptInterface
        fun onStoreSelected(name: String, phone: String, address: String) {
            val intent = Intent()
            intent.putExtra("name", name)
            intent.putExtra("phone", phone)
            intent.putExtra("address", address)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }

        @JavascriptInterface
        fun log(message: String) {
            android.util.Log.d("JANJAN", message)
        }
    }
}