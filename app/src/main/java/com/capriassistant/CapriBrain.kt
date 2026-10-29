package com.capriassistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CapriBrain private constructor(private val context: Context) {
    private val prefs = Prefs(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun commentOnScreen(
        mode: ScreenModeDetector.Mode,
        appLabel: String,
        texts: List<String>,
        imageB64: String?,
        onResult: (String?) -> Unit
    ) {
        val systemPrompt = buildPrompt(mode, appLabel)
        val textContext = if (texts.isNotEmpty()) "Textos na tela: ${texts.take(8).joinToString(" | ")}" else ""
        val userPrompt = "App em foco: $appLabel. $textContext"

        callGemini(systemPrompt, userPrompt, imageB64) { reply ->
            val clean = reply?.trim()
            if (clean != null && !clean.uppercase().startsWith("SKIP")) {
                onResult(clean)
            } else {
                onResult(null)
            }
        }
    }

    fun chat(message: String, onResult: (String) -> Unit) {
        val lower = message.lowercase()
        if (lower.contains("acha") || lower.contains("busca") || lower.contains("procura") || lower.contains("cade") || lower.contains("cadê")) {
            val cleanQuery = lower.replace(Regex("capri|acha|procura|busca|o|a|meu|minha|arquivo|cadê|cade|para mim|por favor"), "").trim()
            if (cleanQuery.isNotEmpty()) {
                val res = FileTools.searchMediaStore(context, cleanQuery)
                onResult(res)
                return
            }
        }

        val systemPrompt = "Você é a Capri (Cyberpunk: Edgerunners 2). Fale em pt-BR, de forma concisa (máximo 2 frases curtas), zoeira, leal e direta. Sem rodeios."
        callGemini(systemPrompt, message, null) { reply ->
            onResult(reply ?: "Deu glitch na conexão com a rede, choom.")
        }
    }

    private fun buildPrompt(mode: ScreenModeDetector.Mode, appLabel: String): String {
        val base = "Você é a Capri, mascote de tela com personalidade debochada e leal. Diga no máximo 1 ou 2 frases curtas."
        return when (mode) {
            ScreenModeDetector.Mode.GAME -> "$base Você está assistindo o usuário jogar $appLabel. Se ele morrer ou tomar dano, dê uma zoada carinhosa. Se estiver em chefe ou build, dê uma dica prática rápida. Se for só tela de menu/loading parado, responda APENAS: SKIP"
            ScreenModeDetector.Mode.VIDEO -> "$base Você está no sofá assistindo ao vídeo de $appLabel junto com ele. Comente a cena com surpresa ou risada. Se for propaganda ou cena sem graça, responda APENAS: SKIP"
            ScreenModeDetector.Mode.WHATSAPP -> "$base Você está espiando o WhatsApp do usuário. Comente o que a outra pessoa mandou, sugira uma resposta curta e sagaz ou fale da fofoca. Se não tiver mensagem nova relevante, responda APENAS: SKIP"
            ScreenModeDetector.Mode.GERAL -> "$base O usuário está no app $appLabel. Se houver algo interessante comente, senão responda APENAS: SKIP"
        }
    }

    private fun callGemini(system: String, userText: String, imageB64: String?, onDone: (String?) -> Unit) {
        if (prefs.apiKey.isBlank()) {
            mainHandler.post { onDone("Falta a chave API do Gemini no painel do app!") }
            return
        }

        Thread {
            try {
                val messages = JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", system))
                    val userContent = if (imageB64 != null) {
                        JSONArray().apply {
                            put(JSONObject().put("type", "text").put("text", userText))
                            put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageB64")))
                        }
                    } else {
                        userText
                    }
                    put(JSONObject().put("role", "user").put("content", userContent))
                }

                val bodyJson = JSONObject().apply {
                    put("model", prefs.model.ifBlank { "gemini-1.5-flash" })
                    put("messages", messages)
                    put("max_tokens", 150)
                }

                val request = Request.Builder()
                    .url("${prefs.baseUrl}/chat/completions")
                    .addHeader("Authorization", "Bearer ${prefs.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        mainHandler.post { onDone(null) }
                        return@use
                    }
                    val text = JSONObject(raw).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content")
                    mainHandler.post { onDone(text) }
                }
            } catch (_: Exception) {
                mainHandler.post { onDone(null) }
            }
        }.start()
    }

    companion object {
        @Volatile private var instance: CapriBrain? = null
        fun get(context: Context): CapriBrain =
            instance ?: synchronized(this) { instance ?: CapriBrain(context.applicationContext).also { instance = it } }
    }
}
