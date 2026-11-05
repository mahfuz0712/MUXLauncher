package com.mux.launcher

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class GestureAction(val label: String) {
    NONE("NONE"),
    DRAWER("APP DRAWER"),
    SEARCH("SEARCH"),
    NOTIFICATIONS("NOTIFICATIONS"),
    QUICK("QUICK PANEL"),
    RECENTS("RECENT APPS"),
    BACK("BACK"),
    HOME("HOME"),
    LOCK("LOCK SCREEN"),
    SHOT("SCREENSHOT"),
    SPLIT("SPLIT SCREEN"),
    TORCH("FLASHLIGHT"),
    SETTINGS("SETTINGS"),
    OVERVIEW("HOME EDITOR"),
    DOCK("EDGE DOCK");

    fun next(): GestureAction = values()[(ordinal + 1) % values().size]

    companion object {
        fun from(name: String?, def: GestureAction): GestureAction =
            values().firstOrNull { it.name == name } ?: def
    }
}

/** Lets the service (or anything else) ask the launcher screen to open something. */
object UiBus {
    var tick by mutableIntStateOf(0)
    var req by mutableStateOf("")

    fun post(r: String) {
        req = r
        tick += 1
    }
}

object Actions {
    private var torchOn = false

    /** Run any gesture action from anywhere (launcher screen, edge zones...). */
    fun run(ctx: Context, a: GestureAction) {
        when (a) {
            GestureAction.NONE -> {}
            GestureAction.DRAWER -> ui(ctx, "drawer")
            GestureAction.SEARCH -> ui(ctx, "search")
            GestureAction.SETTINGS -> ui(ctx, "settings")
            GestureAction.OVERVIEW -> ui(ctx, "overview")
            GestureAction.DOCK -> {
                val s = MuxService.instance
                if (s != null) s.openDock(LauncherPrefs.edgeLeft || !LauncherPrefs.edgeRight) else needService(ctx)
            }
            GestureAction.TORCH -> torch(ctx)
            else -> system(ctx, a)
        }
    }

    private fun ui(ctx: Context, req: String) {
        UiBus.post(req)
        if (ctx !is Activity) {
            try {
                ctx.startActivity(
                    Intent(ctx, MainActivity::class.java)
                        .putExtra("req", req)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            } catch (e: Exception) {
            }
        }
    }

    fun needService(ctx: Context) = Util.toast(ctx, "TURN ON MUX SERVICE (SETTINGS > SETUP)")

    private fun system(ctx: Context, a: GestureAction) {
        val s = MuxService.instance
        if (s == null) {
            when (a) {
                GestureAction.NOTIFICATIONS -> if (!statusBar(ctx, "expandNotificationsPanel")) needService(ctx)
                GestureAction.QUICK -> if (!statusBar(ctx, "expandSettingsPanel")) needService(ctx)
                GestureAction.HOME -> goHome(ctx)
                else -> needService(ctx)
            }
            return
        }
        val ok = when (a) {
            GestureAction.NOTIFICATIONS -> s.global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            GestureAction.QUICK -> s.global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            GestureAction.RECENTS -> s.global(AccessibilityService.GLOBAL_ACTION_RECENTS)
            GestureAction.BACK -> s.global(AccessibilityService.GLOBAL_ACTION_BACK)
            GestureAction.HOME -> s.global(AccessibilityService.GLOBAL_ACTION_HOME)
            GestureAction.SPLIT -> s.global(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            GestureAction.LOCK ->
                if (Build.VERSION.SDK_INT >= 28) s.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) else false
            GestureAction.SHOT ->
                if (Build.VERSION.SDK_INT >= 28) s.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) else false
            else -> true
        }
        if (!ok) Util.toast(ctx, "NOT SUPPORTED ON THIS PHONE")
    }

    fun goHome(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (e: Exception) {
        }
    }

    @SuppressLint("WrongConstant")
    private fun statusBar(ctx: Context, method: String): Boolean = try {
        val sb = ctx.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod(method).invoke(sb)
        true
    } catch (e: Throwable) {
        false
    }

    private fun torch(ctx: Context) {
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (id == null) {
                Util.toast(ctx, "NO FLASHLIGHT")
                return
            }
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
        } catch (e: Exception) {
            Util.toast(ctx, "FLASHLIGHT FAILED")
        }
    }
}

/** Opening apps: full screen, floating window, split screen. */
object Launch {
    private val main = Handler(Looper.getMainLooper())

    fun open(ctx: Context, e: AppEntry) {
        try {
            ctx.startActivity(AppRepo.intentFor(e))
        } catch (ex: Exception) {
            Util.toast(ctx, "CANNOT OPEN " + e.label)
        }
    }

    /** Needs a phone that allows freeform windows; otherwise the app just opens normally. */
    fun floating(ctx: Context, e: AppEntry) {
        try {
            val dm = ctx.resources.displayMetrics
            val w = dm.widthPixels
            val h = dm.heightPixels
            val r = Rect((w * 0.06f).toInt(), (h * 0.18f).toInt(), (w * 0.94f).toInt(), (h * 0.72f).toInt())
            val o = ActivityOptions.makeBasic().setLaunchBounds(r)
            ctx.startActivity(AppRepo.intentFor(e).addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK), o.toBundle())
        } catch (ex: Exception) {
            open(ctx, e)
        }
    }

    /**
     * Split screen: the app that is on screen now stays in the top half, [e] opens in the other half.
     * If split screen is already on, [e] just opens in the other half.
     */
    fun splitBeside(ctx: Context, e: AppEntry) {
        val s = MuxService.instance
        if (s == null) {
            Actions.needService(ctx)
            open(ctx, e)
            return
        }
        fun launchAdjacent() {
            try {
                ctx.startActivity(
                    AppRepo.intentFor(e).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                )
            } catch (ex: Exception) {
                open(ctx, e)
            }
        }
        if (s.inSplit()) {
            launchAdjacent()
            return
        }
        if (!s.global(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)) {
            Util.toast(ctx, "SPLIT SCREEN NOT SUPPORTED ON THIS PHONE")
            open(ctx, e)
            return
        }
        // wait until the system really shows two windows, then open the second app
        var tries = 0
        val poll = object : Runnable {
            override fun run() {
                tries++
                if (s.inSplit() || tries >= 14) {
                    main.postDelayed({ launchAdjacent() }, if (s.inSplit()) 350L else 0L)
                } else {
                    main.postDelayed(this, 250)
                }
            }
        }
        main.postDelayed(poll, 400)
    }

    /** Open [a] first, then split the screen and put [b] next to it. */
    fun splitPair(ctx: Context, a: AppEntry, b: AppEntry) {
        open(ctx, a)
        main.postDelayed({ splitBeside(ctx, b) }, 1300)
    }

    /** Open by the dock mode: FULL, FLOAT or SPLIT. */
    fun byMode(ctx: Context, e: AppEntry, mode: String) {
        when (mode) {
            "FLOAT" -> floating(ctx, e)
            "SPLIT" -> splitBeside(ctx, e)
            else -> open(ctx, e)
        }
    }
}
