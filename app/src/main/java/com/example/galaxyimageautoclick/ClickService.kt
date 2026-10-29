package com.example.galaxyautoclick

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.io.File
import kotlin.math.abs
import kotlin.math.max

class ClickService : AccessibilityService() {
    companion object { var instance: ClickService? = null }
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("config", MODE_PRIVATE) }
    private var busy = false
    private var generation = 0
    override fun onServiceConnected() { instance = this; prefs.edit().putBoolean("running", false).apply() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopLoop() }
    override fun onDestroy() { stopLoop(); instance = null; super.onDestroy() }
    fun stopLoop() { prefs.edit().putBoolean("running", false).apply(); generation++; busy = false; handler.removeCallbacksAndMessages(null) }
    fun startLoop() { generation++; handler.removeCallbacksAndMessages(null); busy = false; if (prefs.getBoolean("running", false)) schedule(0, generation) }
    private fun schedule(delay: Long, token: Int) { handler.postDelayed({ if (token == generation && prefs.getBoolean("running", false)) capture(token) }, delay) }
    private fun capture(token: Int) {
        if (busy) return
        val templateFile = File(filesDir, "template.png")
        if (!templateFile.exists()) { stopLoop(); return }
        busy = true
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    var bitmap: Bitmap? = null
                    try {
                        
val hardwareBitmap = Bitmap.wrapHardwareBuffer(
    result.hardwareBuffer,
    result.colorSpace
)
bitmap = hardwareBitmap?.copy(
    Bitmap.Config.ARGB_8888,
    false
)
hardwareBitmap?.recycle()
  
                        val template = BitmapFactory.decodeFile(templateFile.path)
                        if (bitmap != null && template != null && token == generation && prefs.getBoolean("running", false)) {
                            val point = findTemplate(bitmap, template)
                            if (point != null) tap(point.first, point.second)
                        }
                    } catch (_: Exception) {} finally {
                        bitmap?.recycle(); result.hardwareBuffer.close(); busy = false
                        if (token == generation) schedule(prefs.getInt("interval", 2) * 1000L, token)
                    }
                }
                override fun onFailure(errorCode: Int) { busy = false; if (token == generation) schedule(3000, token) }
            })
        } catch (_: Exception) { busy = false; if (token == generation) schedule(3000, token) }
    }
    private fun tap(x: Int, y: Int) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 80)).build(), null, null)
    }
    // Basic pixel-template matching. Exact-size templates work best; this is not AI/object recognition.
    private fun findTemplate(screen: Bitmap, template: Bitmap): Pair<Int, Int>? {
        val w = screen.width; val h = screen.height; val tw = template.width; val th = template.height
        if (tw > w || th > h) return null
        val sample = 8; val step = max(3, minOf(tw, th) / 8)
        val sx = IntArray(sample * sample) { (it % sample) * (tw - 1) / (sample - 1) }
        val sy = IntArray(sample * sample) { (it / sample) * (th - 1) / (sample - 1) }
        val tpl = IntArray(sample * sample) { template.getPixel(sx[it], sy[it]) }
        var best = Double.MAX_VALUE; var bx = -1; var by = -1
        for (y in 0..(h - th) step step) for (x in 0..(w - tw) step step) {
            var sum = 0L
            for (i in tpl.indices) {
                val a = screen.getPixel(x + sx[i], y + sy[i]); val b = tpl[i]
                sum += abs((a shr 16 and 255) - (b shr 16 and 255)) + abs((a shr 8 and 255) - (b shr 8 and 255)) + abs((a and 255) - (b and 255))
            }
            val score = sum.toDouble() / (tpl.size * 3 * 255)
            if (score < best) { best = score; bx = x; by = y }
        }
        return if (best < 0.09 && bx >= 0) Pair(bx + tw / 2, by + th / 2) else null
    }
}
