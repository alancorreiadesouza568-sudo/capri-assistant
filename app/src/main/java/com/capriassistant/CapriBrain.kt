package com.capriassistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.capriassistant.accessibility.CapriAccessibilityService.ScreenContextSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * CapriBrain (versão nativa, porta do antigo CapriBrain.ts)
 *
 * Mantém o histórico da conversa e fala com qualquer API compatível com o
 * formato OpenAI (/chat/completions) — por padrão o Gemini, que tem camada
 * gratuita. Tudo roda em thread de fundo; o resultado volta na thread principal.
 */
class CapriBrain private constructor(context: Context) {

    class Result(val text: String?, val error: String?)

    private val prefs = Prefs(context)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    // Só mexido dentro do executor (single thread), então é seguro.
    private val history = ArrayList<Pair<String, String>>() // (role, content)

    /** Conversa livre. */
    fun chat(userMessage: String, onResult: (Result) -> Unit) {
        run(PERSONALITY, userMessage, onResult)
    }

    /** Dica de jogo, opcionalmente com o texto visível na tela. */
    fun gameTip(
        gameName: String,
        question: String,
        screen: ScreenContextSnapshot?,
        onResult: (Result) -> Unit
    ) {
        val ctx = if (screen != null && screen.visibleTexts.isNotEmpty()) {
            "\n\nTextos visíveis na tela: " + screen.visibleTexts.take(10).joinToString(", ")
        } else ""

        val system = PERSONALITY + "\n\nModo atual: DICA DE JOGO. Foque em utilidade prática " +
            "(build, item, estratégia). Máximo 3-4 frases. Se não tiver certeza sobre a meta " +
            "atual de \"$gameName\", diga isso em vez de inventar."

        run(system, "$question\n\nJogo: $gameName$ctx", onResult)
    }

    fun resetHistory() {
        executor.execute { history.clear() }
    }

    private fun run(systemPrompt: String, userContent: String, onResult: (Result) -> Unit) {
        executor.execute {
            val result = try {
                if (prefs.apiKey.isEmpty()) {
                    Result(null, "Falta a chave da API. Cole ela no painel do app.")
                } else {
                    addHistory("user", userContent)
                    val text = callApi(systemPrompt)
                    addHistory("assistant", text)
                    Result(text, null)
                }
            } catch (e: Exception) {
                // Não deixa mensagem de erro envenenar o histórico
                if (history.isNotEmpty() && history.last().first == "user") {
                    history.removeAt(history.size - 1)
                }
                Result(null, e.message ?: "Erro desconhecido")
            }
            main.post { onResult(result) }
        }
    }

    private fun addHistory(role: String, content: String) {
        history.add(role to content)
        while (history.size > MAX_HISTORY) history.removeAt(0)
    }

    private fun callApi(systemPrompt: String): String {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        for ((role, content) in history) {
            messages.put(JSONObject().put("role", role).put("content", content))
        }

        val body = JSONObject()
            .put("model", prefs.model)
            .put("messages", messages)
            .put("max_tokens", 1024)

        val conn = URL(prefs.baseUrl + "/chat/completions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 45_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${prefs.apiKey}")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() } ?: ""

            if (code !in 200..299) {
                throw RuntimeException(friendlyError(code, raw))
            }

            val content = JSONObject(raw)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content", "")
            return content.ifBlank { "(sem resposta)" }
        } finally {
            conn.disconnect()
        }
    }

    private fun friendlyError(code: Int, raw: String): String = when (code) {
        400, 404 -> "Erro $code: confira o nome do modelo no painel. ${raw.take(160)}"
        401, 403 -> "Chave da API recusada ($code). Confira se colou certo."
        429 -> "Limite gratuito atingido (429). Espere um pouco e tente de novo."
        else -> "Erro $code do servidor. ${raw.take(160)}"
    }

    companion object {
        private const val MAX_HISTORY = 20

        private const val PERSONALITY = """Você é a Capri, uma assistente de IA carismática, direta e confiante, que acompanha o usuário enquanto ele joga e usa o celular. Responda sempre em português do Brasil.
- Dê dicas objetivas de performance em jogos (builds, itens, estratégias) quando perguntada.
- Converse casualmente quando o usuário quiser papo, sem forçar assunto.
- Seja concisa: respostas curtas (no máximo 3-4 frases), pois o usuário está jogando.
- Nunca invente informação de jogo. Se não tiver certeza da build/meta atual, avise que pode estar desatualizada.
Você só sabe o que o usuário te contou ou o que veio no contexto de tela enviado a você."""

        @Volatile private var instance: CapriBrain? = null

        fun get(context: Context): CapriBrain =
            instance ?: synchronized(this) {
                instance ?: CapriBrain(context.applicationContext).also { instance = it }
            }
    }
}
