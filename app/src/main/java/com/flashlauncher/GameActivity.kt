package com.flashlauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.WebViewAssetLoader
import java.io.File

const val BASE = "https://appassets.androidplatform.net"

abstract class GameActivity : Activity() {
    abstract val slot: String
    abstract val other: Class<*>

    private val BG = Color.parseColor("#09090B")
    private val OR = Color.parseColor("#FF7A1A")
    private lateinit var web: WebView
    private lateinit var err: LinearLayout
    private lateinit var tools: LinearLayout
    private lateinit var swfDir: File
    private var lastUrl = "$BASE/assets/home.html"
    private val prefs by lazy { getSharedPreferences("s_$slot", MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun chip(t: String, on: Boolean = false, a: () -> Unit) = TextView(this).apply {
        text = t; gravity = Gravity.CENTER; textSize = 16f
        setTextColor(if (on) BG else OR)
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(if (on) OR else Color.parseColor("#CC111111"))
            setStroke(dp(1), OR)
        }
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { bottomMargin = dp(6) }
        setOnClickListener { a() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(s: Bundle?) {
        if (Build.VERSION.SDK_INT >= 28 && slot == "b") WebView.setDataDirectorySuffix("b")
        super.onCreate(s)
        swfDir = File(filesDir, "swf").apply { mkdirs() }

        val assets = WebViewAssetLoader.AssetsPathHandler(this)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/") { p ->
                assets.handle(p)?.also {
                    it.mimeType = when {
                        p.endsWith(".js") -> "text/javascript"
                        p.endsWith(".wasm") -> "application/wasm"
                        p.endsWith(".html") -> "text/html"
                        else -> it.mimeType
                    }
                }
            }
            .addPathHandler("/files/", WebViewAssetLoader.InternalStoragePathHandler(this, swfDir))
            .build()

        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(r.url)
                override fun onPageStarted(v: WebView, url: String, f: android.graphics.Bitmap?) {
                    lastUrl = url; err.visibility = View.GONE
                    prefs.edit().putString("u", url).apply()
                }
                override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                    if (r.isForMainFrame) err.visibility = View.VISIBLE
                }
            }
        }

        val root = FrameLayout(this).apply { setBackgroundColor(BG) }
        root.addView(web, ViewGroup.LayoutParams(-1, -1))

        err = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(BG); visibility = View.GONE
            addView(TextView(context).apply {
                text = "Error de carga"; textSize = 20f; setTextColor(OR); gravity = Gravity.CENTER
            })
            addView(Button(context).apply {
                text = "↻ Recargar"; setOnClickListener { go(lastUrl) }
            })
        }
        root.addView(err, ViewGroup.LayoutParams(-1, -1))

        val wins = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(chip("A", slot == "a") { switchTo("a") })
            addView(chip("B", slot == "b") { switchTo("b") })
        }
        root.addView(wins, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.TOP).apply {
            leftMargin = dp(6); topMargin = dp(40)
        })

        tools = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(chip("↻") { if (err.visibility == View.VISIBLE) go(lastUrl) else web.reload() })
            addView(chip("☰") { menu() })
        }
        root.addView(tools, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.TOP).apply {
            rightMargin = dp(6); topMargin = dp(40)
        })

        setContentView(root)
        go(prefs.getString("u", null) ?: lastUrl)
    }

    private fun go(u: String) { err.visibility = View.GONE; web.loadUrl(u) }

    private fun switchTo(t: String) {
        if (t == slot) return
        startActivity(Intent(this, other).addFlags(
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    private fun menu() {
        val items = arrayOf("Abrir dirección", "Abrir SWF local (Ruffle)", "Inicio",
            "Ocultar/mostrar herramientas")
        AlertDialog.Builder(this).setItems(items) { _, i ->
            when (i) {
                0 -> {
                    val e = EditText(this).apply { setText("https://") }
                    AlertDialog.Builder(this).setView(e).setPositiveButton("Ir") { _, _ ->
                        var u = e.text.toString().trim()
                        if (!u.contains("://")) u = "https://$u"
                        go(u)
                    }.show()
                }
                1 -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 7)
                2 -> go("$BASE/assets/home.html")
                3 -> tools.visibility = if (tools.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
            }
        }.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        super.onActivityResult(rq, rs, d)
        if (rq != 7 || rs != RESULT_OK) return
        val u = d?.data ?: return
        val f = File(swfDir, "game_$slot.swf")
        contentResolver.openInputStream(u)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        go("$BASE/assets/player.html?swf=/files/${f.name}&t=${System.currentTimeMillis()}")
    }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(h: Boolean) {
        super.onWindowFocusChanged(h)
        if (h) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else moveTaskToBack(true) }

    override fun onDestroy() { web.destroy(); super.onDestroy() }
}

class WindowA : GameActivity() {
    override val slot = "a"
    override val other: Class<*> get() = WindowB::class.java
}

class WindowB : GameActivity() {
    override val slot = "b"
    override val other: Class<*> get() = WindowA::class.java
}
