package com.mux.launcher

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * The engine behind gestures and the edge dock.
 * It performs system actions (back, home, recents, split screen...) and draws invisible edge zones.
 * It never reads screen text.
 */
class MuxService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: MuxService? = null
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var dock: EdgeDock? = null
    private var edge: EdgeStrips? = null
    private var strips: GestureStrips? = null
    private val listener: () -> Unit = { handler.post { rebuild() } }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LauncherPrefs.init(this)
        AppRepo.ensure(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!LauncherPrefs.listeners.contains(listener)) LauncherPrefs.listeners.add(listener)
        rebuild()
    }

    private fun rebuild() {
        try {
            val d = dock ?: EdgeDock(this, wm).also { dock = it }
            if (edge == null) edge = EdgeStrips(this, wm, d)
            if (strips == null) strips = GestureStrips(this, wm)
            edge?.build()
            strips?.build()
        } catch (e: Exception) {
        }
    }

    override fun onAccessibilityEvent(ev: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        handler.postDelayed({ rebuild() }, 250)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        LauncherPrefs.listeners.remove(listener)
        edge?.destroy()
        dock?.destroy()
        strips?.destroy()
        instance = null
        return super.onUnbind(intent)
    }

    fun global(action: Int): Boolean = performGlobalAction(action)

    /** True when two app windows are on screen (split screen). Only counts windows, reads nothing. */
    fun inSplit(): Boolean = try {
        windows.count { it.type == AccessibilityWindowInfo.TYPE_APPLICATION } >= 2
    } catch (e: Exception) {
        false
    }

    fun openDock(left: Boolean) {
        dock?.open(left)
    }
}
