package com.capriassistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Lê os TEXTOS visíveis da tela atual (a mesma API usada por leitores de tela).
 * Não captura imagem. Só guarda o último estado em memória — nada é enviado
 * para a internet aqui. O envio só acontece quando o usuário TOCA na Capri
 * e já definiu um jogo no painel (ver OverlayService).
 */
class CapriAccessibilityService : AccessibilityService() {

    data class ScreenContextSnapshot(
        val appPackageName: String?,
        val visibleTexts: List<String>,
        val timestamp: Long
    )

    companion object {
        @Volatile var latest: ScreenContextSnapshot? = null
        private const val MIN_MS_BETWEEN_CAPTURES = 1_000L
    }

    private var lastCaptureAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()
        if (pkg == null || pkg == packageName) return // ignora o próprio app

        // Limita a frequência: jogos disparam eventos o tempo todo.
        val now = System.currentTimeMillis()
        if (now - lastCaptureAt < MIN_MS_BETWEEN_CAPTURES) return
        lastCaptureAt = now

        val root = rootInActiveWindow ?: return
        val texts = ArrayList<String>()
        collect(root, texts, 0)

        latest = ScreenContextSnapshot(pkg, texts.take(20), now)
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 12 || out.size > 50) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        latest = null
    }
}
