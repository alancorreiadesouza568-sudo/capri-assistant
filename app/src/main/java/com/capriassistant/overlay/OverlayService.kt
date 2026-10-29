package com.capriassistant.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import com.capriassistant.*
import com.capriassistant.accessibility.CapriAccessibilityService
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var overlayView: View
    private lateinit var webView: WebView
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var brain: CapriBrain
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private var startX = 0; private var startY = 0
    private var touchX = 0f; private var touchY = 0f
    private var dragging = false

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        brain = CapriBrain.get(this)
        startAsForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        setupView()
        handler.postDelayed(autoWatchLoop, 15_000L)
    }

    private fun startAsForeground() {
        val channelId = "capri_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "Capri Ativa", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Capri na Tela")
            .setContentText("Acompanhando seu celular em silêncio.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true).build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupView() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_capri, null)
        webView = overlayView.findViewById(R.id.capri_webview)

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 400
        }

        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.addJavascriptInterface(AndroidBridge(), "AndroidCapri")
        webView.loadUrl("file:///android_asset/capri/index.html")

        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = event.rawX; touchY = event.rawY
                    dragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) dragging = true
                    if (dragging) {
                        lp.x = startX + dx
                        lp.y = startY + dy
                        wm.updateViewLayout(overlayView, lp)
                    }
                    true
                }
                else -> false
            }
        }
        wm.addView(overlayView, lp)
    }

    fun sendBubbleText(text: String) {
        val safe = text.replace("'", "\\'").replace("\n", "<br>")
        webView.post {
            webView.evaluateJavascript("window.showSpeech('$safe');", null)
        }
    }

    private val autoWatchLoop = object : Runnable {
        override fun run() {
            val pkg = CapriAccessibilityService.currentPackage
            val accessibility = CapriAccessibilityService.instance
            if (pkg != null && accessibility != null) {
                val mode = ScreenModeDetector.detect(this@OverlayService, pkg)
                val label = try {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) { pkg }

                if (mode == ScreenModeDetector.Mode.GAME || mode == ScreenModeDetector.Mode.VIDEO) {
                    accessibility.takeJpegBase64 { img ->
                        brain.commentOnScreen(mode, label, emptyList(), img) { reply ->
                            reply?.let { sendBubbleText(it) }
                        }
                    }
                } else if (mode == ScreenModeDetector.Mode.WHATSAPP) {
                    brain.commentOnScreen(mode, label, CapriAccessibilityService.latestTexts, null) { reply ->
                        reply?.let { sendBubbleText(it) }
                    }
                }
            }
            handler.postDelayed(this, (prefs.intervalSec * 1000L).coerceAtLeast(15_000L))
        }
    }

    private fun showChatDialog() {
        val input = EditText(this).apply {
            hint = "Fale com a Capri ou peça um arquivo..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Capri")
            .setView(input)
            .setPositiveButton("Enviar") { _, _ ->
                val txt = input.text.toString().trim()
                if (txt.isNotEmpty()) {
                    brain.chat(txt) { reply -> sendBubbleText(reply) }
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun onPoke() {
            val falas = listOf(
                "Tô de olho aqui, pode continuar!",
                "E aí, choom! Precisa que eu ache algum arquivo?",
                "Cuidado com os glitches ⚡",
                "Tô assistindo com você, relaxa!"
            )
            sendBubbleText(falas.random())
        }

        @JavascriptInterface
        fun openChat() {
            handler.post { showChatDialog() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (::overlayView.isInitialized) wm.removeView(overlayView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
