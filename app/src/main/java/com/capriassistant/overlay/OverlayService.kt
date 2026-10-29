package com.capriassistant.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.capriassistant.CapriBrain
import com.capriassistant.Prefs
import com.capriassistant.R
import com.capriassistant.accessibility.CapriAccessibilityService
import kotlin.math.abs

/**
 * Desenha a Capri (avatar + balão) por cima de outros apps.
 * Toque simples = pede uma dica à IA. Arrastar = move a Capri.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var avatarImage: ImageView
    private lateinit var speechBubble: TextView
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 300
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isDragging = false
    private var waitingAnswer = false

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlayView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val channelId = "capri_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Capri ativa", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Capri ativa")
            .setContentText("Toque nela para pedir uma dica.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun setupOverlayView() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_capri, null)
        avatarImage = overlayView.findViewById(R.id.avatar_image)
        speechBubble = overlayView.findViewById(R.id.speech_bubble)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        setupDragAndTap()
        windowManager.addView(overlayView, layoutParams)
    }

    private fun setupDragAndTap() {
        avatarImage.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) isDragging = true
                    if (isDragging) {
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        windowManager.updateViewLayout(overlayView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) onAvatarTapped()
                    true
                }
                else -> false
            }
        }
    }

    private fun onAvatarTapped() {
        // Balão aberto: o toque fecha.
        if (speechBubble.visibility == View.VISIBLE && !waitingAnswer) {
            speechBubble.visibility = View.GONE
            return
        }
        if (waitingAnswer) return

        val prefs = Prefs(this)
        val brain = CapriBrain.get(this)
        waitingAnswer = true
        showBubble("Pensando...")

        val onResult: (CapriBrain.Result) -> Unit = { r ->
            waitingAnswer = false
            showBubble(r.text ?: r.error ?: "Algo deu errado.")
        }

        val game = prefs.game
        if (game.isNotEmpty()) {
            // Contexto de tela só é enviado quando há um jogo definido e o dado é recente.
            val screen = CapriAccessibilityService.latest
                ?.takeIf { System.currentTimeMillis() - it.timestamp < 30_000 }
            brain.gameTip(game, "Com base no que está na tela agora, alguma dica rápida?", screen, onResult)
        } else {
            brain.chat("Diga oi em uma frase curta e pergunte como pode ajudar.", onResult)
        }
    }

    private fun showBubble(text: String) {
        speechBubble.text = text
        speechBubble.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
    }
}
