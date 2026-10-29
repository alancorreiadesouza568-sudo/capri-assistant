package com.capriassistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.capriassistant.overlay.OverlayService

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var statusText: TextView
    private lateinit var apiKeyInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        statusText = findViewById(R.id.status_text)
        apiKeyInput = findViewById(R.id.input_api_key)
        apiKeyInput.setText(prefs.apiKey)

        findViewById<Button>(R.id.btn_overlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            prefs.apiKey = apiKeyInput.text.toString().trim()
            if (!Settings.canDrawOverlays(this)) {
                statusText.text = "Ative a permissão de sobreposição primeiro!"
                return@setOnClickListener
            }
            ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
            statusText.text = "Capri ativa na tela! Pode fechar este painel."
        }

        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            statusText.text = "Capri desativada."
        }
    }
}
