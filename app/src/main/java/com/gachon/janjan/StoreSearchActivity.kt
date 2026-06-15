package com.gachon.janjan

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gachon.janjan.domain.session.repository.KakaoPlace
import com.gachon.janjan.domain.session.repository.KakaoStoreSearchClient
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class StoreSearchActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val searchClient = KakaoStoreSearchClient()

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
        fun searchStores(query: String) {
            lifecycleScope.launch {
                try {
                    val places = searchClient.search(query)
                    webView.evaluateJavascript("renderResults(${places.toJsonArray()});", null)
                } catch (exception: Exception) {
                    Log.e("JANJAN", "Store search failed", exception)
                    val message = JSONObject.quote("검색 중 오류가 발생했습니다")
                    webView.evaluateJavascript("renderError($message);", null)
                }
            }
        }

        @JavascriptInterface
        fun onStoreSelected(
            name: String,
            phone: String,
            address: String,
            kakaoPlaceId: String,
            category: String,
            placeUrl: String,
            roadAddress: String,
            jibunAddress: String
        ) {
            val intent = Intent()
            intent.putExtra("name", name)
            intent.putExtra("phone", phone)
            intent.putExtra("address", address)
            intent.putExtra("kakaoPlaceId", kakaoPlaceId)
            intent.putExtra("category", category)
            intent.putExtra("placeUrl", placeUrl)
            intent.putExtra("roadAddress", roadAddress)
            intent.putExtra("jibunAddress", jibunAddress)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }

        @JavascriptInterface
        fun log(message: String) {
            android.util.Log.d("JANJAN", message)
        }
    }

    private fun List<KakaoPlace>.toJsonArray(): String {
        val places = JSONArray()
        forEach { place ->
            places.put(
                JSONObject().apply {
                    put("id", place.id)
                    put("name", place.name)
                    put("address", place.address)
                    put("category", place.category)
                    put("phone", place.phone)
                    put("placeUrl", place.placeUrl)
                    put("roadAddress", place.roadAddress)
                    put("jibunAddress", place.jibunAddress)
                }
            )
        }
        return places.toString()
    }
}
