package com.mux.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * The engine behind gestures, the edge dock and the window bar.
 * It only watches which app is in front (package name) and performs system actions. It never reads screen text.
 */
class MuxService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: MuxService? = null
    }

    var foreground: String = ""
        private set

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var dock: EdgeDock? = null
    private var bar: WindowBar? = null
    private var strips: GestureStrips? = null
    private var homePkgs: Set<String> = emptySet()
    private val listener: () -> Unit = { handler.post { rebuild() } }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LauncherPrefs.init(this)
        AppRepo.ensure(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        homePkgs = try {
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(q, 0).map { it.activityInfo.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
        if (!LauncherPrefs.listeners.contains(listener)) LauncherPrefs.listeners.add(listener)
        rebuild()
    }

    private fun rebuild() {
        try {
            if (dock == null) dock = EdgeDock(this, wm)
            if (strips == null) strips = GestureStrips(this, wm)
            if (bar == null) bar = WindowBar(this, wm)
            dock?.build()
            strips?.build()
            if (LauncherPrefs.windowBar) bar?.build() else bar?.destroy()
            updateBar()
        } catch (e: Exception) {
        }
    }

    private fun updateBar() {
        val show = LauncherPrefs.windowBar && foreground.isNotEmpty() &&
            foreground != packageName && !homePkgs.contains(foreground)
        bar?.setVisible(show)
    }

    override fun onAccessibilityEvent(ev: AccessibilityEvent?) {
        ev ?: return
        if (ev.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val p = ev.packageName?.toString() ?: return
        val cls = ev.className?.toString() ?: ""
        if (p == "com.android.systemui" || p == "android" || cls.startsWith("android.inputmethodservice")) return
        if (p == packageName) {
            // our own overlays also report here: only the launcher screen itself means "home"
            if (cls == MainActivity::class.java.name) {
                foreground = p
                updateBar()
            }
            return
        }
        foreground = p
        updateBar()
    }

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        handler.postDelayed({ rebuild() }, 250)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        LauncherPrefs.listeners.remove(listener)
        dock?.destroy()
        bar?.destroy()
        strips?.destroy()
        instance = null
        return super.onUnbind(intent)
    }

    fun global(action: Int): Boolean = performGlobalAction(action)

    fun inSplit(): Boolean = try {
        windows.count { it.type == AccessibilityWindowInfo.TYPE_APPLICATION } >= 2
    } catch (e: Exception) {
        false
    }

    fun openDock(left: Boolean) {
        dock?.open(left)
    }
}
