package com.capriassistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.max

class CapriAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var currentPackage: String? = null
        @Volatile var latestTexts: List<String> = emptyList()
        @Volatile var instance: CapriAccessibilityService? = null
    }

    private val io = Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName || pkg.contains("systemui") || pkg.contains("inputmethod")) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPackage = pkg
        }

        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        collect(root, texts, 0)
        latestTexts = texts.take(15)
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 10 || out.size > 25) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1)
        }
    }

    fun takeJpegBase64(onDone: (String?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onDone(null)
            return
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, io, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    try {
                        val hw = result.hardwareBuffer
                        val hwBmp = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                        val soft = hwBmp?.copy(Bitmap.Config.ARGB_8888, false)
                        hw.close()
                        if (soft != null) {
                            val scale = 600f / max(soft.width, soft.height)
                            val scaled = Bitmap.createScaledBitmap(
                                soft, (soft.width * scale).toInt(), (soft.height * scale).toInt(), true
                            )
                            val bos = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, 55, bos)
                            onDone(Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP))
                            return
                        }
                    } catch (_: Exception) {}
                    onDone(null)
                }
                override fun onFailure(errorCode: Int) { onDone(null) }
            })
        } catch (_: Exception) { onDone(null) }
    }

    override fun onInterrupt() {}
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
}
