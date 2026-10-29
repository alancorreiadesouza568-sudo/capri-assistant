package com.capriassistant

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("capri_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v.trim()).apply()

    var model: String
        get() = sp.getString("model", "gemini-1.5-flash") ?: "gemini-1.5-flash"
        set(v) = sp.edit().putString("model", v.trim().ifEmpty { "gemini-1.5-flash" }).apply()

    var baseUrl: String
        get() = sp.getString("base_url", "https://generativelanguage.googleapis.com/v1beta/openai") ?: "https://generativelanguage.googleapis.com/v1beta/openai"
        set(v) = sp.edit().putString("base_url", v.trim().trimEnd('/')).apply()

    var intervalSec: Int
        get() = sp.getInt("interval_sec", 20).coerceAtLeast(15)
        set(v) = sp.edit().putInt("interval_sec", v.coerceAtLeast(15)).apply()
}
