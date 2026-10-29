package com.capriassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.capriassistant.accessibility.CapriAccessibilityService
import com.capriassistant.overlay.OverlayService

/** Painel: permissões, chave da API, jogo atual, chat de teste e botões de iniciar/parar. */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var statusText: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var gameInput: EditText
    private lateinit var chatInput: EditText
    private lateinit var chatOutput: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        statusText = findViewById(R.id.status_text)
        apiKeyInput = findViewById(R.id.input_api_key)
        modelInput = findViewById(R.id.input_model)
        gameInput = findViewById(R.id.input_game)
        chatInput = findViewById(R.id.input_chat)
        chatOutput = findViewById(R.id.chat_output)

        apiKeyInput.setText(prefs.apiKey)
        modelInput.setText(prefs.model)
        gameInput.setText(prefs.game)

        findViewById<Button>(R.id.btn_grant_permission).setOnClickListener { requestOverlayPermission() }
        findViewById<Button>(R.id.btn_grant_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btn_start_capri).setOnClickListener { saveSettings(); startOverlay() }
        findViewById<Button>(R.id.btn_stop_capri).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            statusText.text = "Capri parada."
        }
        findViewById<Button>(R.id.btn_send_chat).setOnClickListener { sendChat() }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }

    private fun saveSettings() {
        prefs.apiKey = apiKeyInput.text.toString()
        prefs.model = modelInput.text.toString()
        prefs.game = gameInput.text.toString()
    }

    private fun sendChat() {
        saveSettings()
        val msg = chatInput.text.toString().trim()
        if (msg.isEmpty()) return
        chatOutput.text = "Pensando..."
        chatInput.setText("")
        CapriBrain.get(this).chat(msg) { r -> chatOutput.text = r.text ?: r.error }
    }

    private fun hasOverlayPermission() = Settings.canDrawOverlays(this)

    private fun hasAccessibilityPermission(): Boolean {
        val expected = "$packageName/${CapriAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (c in splitter) if (c.equals(expected, ignoreCase = true)) return true
        return false
    }

    private fun requestOverlayPermission() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun startOverlay() {
        if (!hasOverlayPermission()) {
            statusText.text = "Conceda a permissão de overlay primeiro."
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
        statusText.text = "Capri ativa! Minimize o app e toque nela para pedir uma dica."
    }

    private fun updateStatus() {
        statusText.text = when {
            !hasOverlayPermission() -> "Falta a permissão de overlay. Toque no primeiro botão."
            !hasAccessibilityPermission() ->
                "Overlay ok. A Acessibilidade é opcional (dá contexto de tela nas dicas)."
            else -> "Tudo pronto. Toque em \"Iniciar Capri\"."
        }
    }
}
