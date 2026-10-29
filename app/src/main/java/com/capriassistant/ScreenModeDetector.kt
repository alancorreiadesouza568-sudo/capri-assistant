package com.capriassistant

import android.content.Context
import android.content.pm.ApplicationInfo

object ScreenModeDetector {
    enum class Mode { WHATSAPP, GAME, VIDEO, GERAL }

    private val VIDEO_PACKAGES = setOf(
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.netflix.mediaclient",
        "tv.twitch.android.app",
        "com.crunchyroll.crunchyroid"
    )

    fun detect(context: Context, pkg: String?): Mode {
        if (pkg == null) return Mode.GERAL
        if (pkg.contains("whatsapp")) return Mode.WHATSAPP
        if (pkg in VIDEO_PACKAGES) return Mode.VIDEO

        return try {
            val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
            val isGame = (appInfo.category == ApplicationInfo.CATEGORY_GAME) ||
                    (appInfo.flags and ApplicationInfo.FLAG_IS_GAME != 0)
            if (isGame) Mode.GAME else Mode.GERAL
        } catch (_: Exception) {
            Mode.GERAL
        }
    }
}
