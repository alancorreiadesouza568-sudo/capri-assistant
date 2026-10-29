package com.capriassistant

import android.content.Context

/** Configurações salvas no próprio celular (SharedPreferences privado do app). */
class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("capri_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v.trim()).apply()

    var model: String
        get() = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = sp.edit().putString("model", v.trim().ifEmpty { DEFAULT_MODEL }).apply()

    var baseUrl: String
        get() = sp.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(v) = sp.edit().putString("base_url", v.trim().trimEnd('/').ifEmpty { DEFAULT_BASE_URL }).apply()

    var game: String
        get() = sp.getString("game", "") ?: ""
        set(v) = sp.edit().putString("game", v.trim()).apply()

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai"
        const val DEFAULT_MODEL = "gemini-flash-latest"
    }
}
