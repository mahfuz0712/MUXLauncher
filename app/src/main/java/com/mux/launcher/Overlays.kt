package com.mux.launcher

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

fun Context.px(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

object Pal {
    private val p: HudPalette get() = if (LauncherPrefs.amber) AmberPalette else CyanPalette
    val primary: Int get() = p.primary.toArgb()
    val dim: Int get() = p.dim.toArgb()
    val bg: Int get() = p.bg.toArgb()
    val panel: Int get() = p.panel.toArgb()
    val accent: Int get() = p.accent.toArgb()
    val danger: Int get() = p.danger.toArgb()
    val text: Int get() = p.text.toArgb()
}

/** Panel with two cut corners and a glowing outline - the HUD frame, drawn with plain Views. */
class HudFrame(ctx: Context) : LinearLayout(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init {
        setWillNotDraw(false)
        fill.style = Paint.Style.FILL
        line.style = Paint.Style.STROKE
        line.strokeWidth = ctx.px(1).toFloat() * 1.5f
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat() - 2f
        val h = height.toFloat() - 2f
        val cut = context.px(12).toFloat()
        path.reset()
        path.moveTo(1f + cut, 1f)
        path.lineTo(1f + w, 1f)
        path.lineTo(1f + w, 1f + h - cut)
        path.lineTo(1f + w - cut, 1f + h)
        path.lineTo(1f, 1f + h)
        path.lineTo(1f, 1f + cut)
        path.close()
        fill.color = (Pal.panel and 0x00FFFFFF) or (0xEE shl 24)
        line.color = Pal.primary
        canvas.drawPath(path, fill)
        canvas.drawPath(path, line)
        super.onDraw(canvas)
    }
}

private fun overlayParams(w: Int, h: Int, gravity: Int, x: Int = 0, y: Int = 0): WindowManager.LayoutParams =
    WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).also {
        it.gravity = gravity
        it.x = x
        it.y = y
    }

private fun label(ctx: Context, text: String, sp: Float, color: Int, bold: Boolean = false): TextView =
    TextView(ctx).apply {
        this.text = text
        textSize = sp
        setTextColor(color)
        typeface = if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

private fun outline(ctx: Context, color: Int, filled: Boolean): GradientDrawable =
    GradientDrawable().apply {
        cornerRadius = ctx.px(4).toFloat()
        setStroke(ctx.px(1), color)
        setColor(if (filled) (color and 0x00FFFFFF) or (0x40 shl 24) else 0x00000000)
    }

/** The edge dock panel. It has no handle: it opens when you swipe in from a screen edge and hold (see [EdgeStrips]). */
class EdgeDock(private val svc: MuxService, private val wm: WindowManager) {
    private var scrim: FrameLayout? = null
    private val main = Handler(Looper.getMainLooper())

    val isOpen: Boolean get() = scrim != null

    fun destroy() {
        close()
    }

    fun close() {
        val s = scrim ?: return
        scrim = null
        try { wm.removeView(s) } catch (e: Exception) { }
    }

    fun open(left: Boolean) {
        close()
        AppRepo.ensure(svc)
        val root = FrameLayout(svc)
        root.setBackgroundColor(0x55000000)
        root.setOnClickListener { close() }

        val panel = HudFrame(svc)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(svc.px(6), svc.px(14), svc.px(6), svc.px(14))

        // Mode chips: FULL / FLOAT / SPLIT
        val chips = ArrayList<TextView>()
        val modeRow = LinearLayout(svc)
        modeRow.orientation = LinearLayout.HORIZONTAL
        fun paintChips() {
            for (c in chips) {
                val on = c.text.toString() == LauncherPrefs.edgeMode
                c.setTextColor(if (on) Pal.accent else Pal.dim)
                c.background = outline(svc, if (on) Pal.accent else Pal.dim, on)
            }
        }
        for (m in listOf("FULL", "FLOAT", "SPLIT")) {
            val c = label(svc, m, 8f, Pal.dim, true)
            c.setPadding(0, svc.px(5), 0, svc.px(5))
            c.setOnClickListener {
                LauncherPrefs.edgeMode = m
                LauncherPrefs.commit(false)
                paintChips()
            }
            chips.add(c)
            modeRow.addView(c, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also {
                it.setMargins(svc.px(1), 0, svc.px(1), 0)
            })
        }
        paintChips()
        panel.addView(modeRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val col = LinearLayout(svc)
        col.orientation = LinearLayout.VERTICAL
        for (e in AppRepo.resolve(LauncherPrefs.edgeApps)) {
            val item = LinearLayout(svc)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER_HORIZONTAL
            item.setPadding(0, svc.px(8), 0, svc.px(2))
            val iv = ImageView(svc)
            iv.setImageBitmap(e.bitmap)
            item.addView(iv, LinearLayout.LayoutParams(svc.px(42), svc.px(42)))
            val tv = label(svc, e.label, 9f, Pal.text)
            item.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            item.setOnClickListener {
                close()
                main.postDelayed({ Launch.byMode(svc, e, LauncherPrefs.edgeMode) }, 150)
            }
            col.addView(item)
        }
        val more = LinearLayout(svc)
        more.orientation = LinearLayout.VERTICAL
        more.gravity = Gravity.CENTER_HORIZONTAL
        more.setPadding(0, svc.px(10), 0, svc.px(2))
        more.addView(label(svc, "◈", 24f, Pal.primary, true))
        more.addView(label(svc, "MORE", 9f, Pal.text))
        more.setOnClickListener {
            close()
            Actions.run(svc, GestureAction.DRAWER)
        }
        col.addView(more)
        val sv = ScrollView(svc)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(col)
        panel.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val g = (if (left) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
        val maxH = (svc.resources.displayMetrics.heightPixels * 0.82f).toInt()
        root.addView(panel, FrameLayout.LayoutParams(svc.px(86), ViewGroup.LayoutParams.WRAP_CONTENT, g).also {
            it.topMargin = (svc.resources.displayMetrics.heightPixels - maxH) / 2
            it.bottomMargin = it.topMargin
        })
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(root, lp)
            scrim = root
            panel.translationX = (if (left) -1 else 1) * svc.px(100).toFloat()
            panel.animate().translationX(0f).setDuration(170).start()
        } catch (e: Exception) {
        }
    }
}

/**
 * Invisible touch zones on the left and right screen edge. Swipe in from the edge and keep holding
 * (default 3 seconds) and the edge dock opens. There is nothing to see on screen.
 * A quick swipe in from the edge is "back" when EDGE BACK is on.
 */
class EdgeStrips(private val svc: MuxService, private val wm: WindowManager, private val dock: EdgeDock) {
    private val views = ArrayList<View>()
    private val main = Handler(Looper.getMainLooper())

    private inner class Strip(val left: Boolean) : View(svc) {
        private var downX = 0f
        private var downY = 0f
        private var swiped = false
        private var fired = false
        private val fire = Runnable {
            fired = true
            Util.vibrate(svc)
            dock.open(left)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    swiped = false
                    fired = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val inward = if (left) dx else -dx
                    if (!swiped && inward > context.px(14)) {
                        swiped = true
                        main.postDelayed(fire, LauncherPrefs.edgeHold.coerceIn(1, 6) * 1000L)
                    }
                    // sliding far up or down is something else (scrolling), not a hold
                    if (swiped && !fired && abs(ev.rawY - downY) > context.px(90)) main.removeCallbacks(fire)
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(fire)
                    val dx = ev.rawX - downX
                    val inward = if (left) dx else -dx
                    if (!fired && swiped && inward > context.px(60) && LauncherPrefs.edgeBack) {
                        Actions.run(svc, GestureAction.BACK)
                    }
                }
                MotionEvent.ACTION_CANCEL -> main.removeCallbacks(fire)
            }
            return true
        }
    }

    fun build() {
        destroy()
        val w = svc.px(LauncherPrefs.edgeW.coerceIn(8, 40))
        // Keep Android's outer system-back gesture inset clear. Accessibility overlays
        // receive touches before app content and can otherwise steal native edge-back swipes.
        val gestureSafeInset = svc.px(32)
        if (LauncherPrefs.edgeLeft) {
            add(Strip(true), overlayParams(w, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START, x = gestureSafeInset))
        }
        if (LauncherPrefs.edgeRight) {
            add(Strip(false), overlayParams(w, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END, x = gestureSafeInset))
        }
    }

    private fun add(v: View, p: WindowManager.LayoutParams) {
        try {
            wm.addView(v, p)
            views.add(v)
        } catch (e: Exception) {
        }
    }

    fun destroy() {
        for (v in views) try { wm.removeView(v) } catch (e: Exception) { }
        views.clear()
    }
}

/** Optional bottom bar: swipe up = home, swipe up and hold = recent apps, swipe sideways = back. */
class GestureStrips(private val svc: MuxService, private val wm: WindowManager) {
    private val views = ArrayList<View>()

    private inner class Strip(val kind: Int) : View(svc) {
        // kind 0 = left edge, 1 = right edge, 2 = bottom bar
        private var dx0 = 0f
        private var dy0 = 0f
        private var t0 = 0L
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            if (kind != 2) return
            paint.color = (Pal.primary and 0x00FFFFFF) or (0x80 shl 24)
            val w = context.px(110).toFloat()
            val h = context.px(3).toFloat()
            val l = (width - w) / 2f
            val t = height - context.px(8).toFloat()
            canvas.drawRoundRect(RectF(l, t, l + w, t + h), h, h, paint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx0 = ev.rawX; dy0 = ev.rawY; t0 = System.currentTimeMillis()
                }
                MotionEvent.ACTION_UP -> {
                    val dx = ev.rawX - dx0
                    val dy = dy0 - ev.rawY
                    val slow = System.currentTimeMillis() - t0 > 380
                    if (kind == 2) {
                        if (dy > context.px(50) && dy > abs(dx)) {
                            Actions.run(svc, if (slow) GestureAction.RECENTS else GestureAction.HOME)
                        } else if (abs(dx) > context.px(60)) {
                            Actions.run(svc, GestureAction.BACK)
                        }
                    } else {
                        val inward = if (kind == 0) dx else -dx
                        if (inward > context.px(60)) Actions.run(svc, GestureAction.BACK)
                    }
                }
            }
            return true
        }
    }

    fun build() {
        destroy()
        if (LauncherPrefs.bottomBar) {
            add(Strip(2), overlayParams(ViewGroup.LayoutParams.MATCH_PARENT, svc.px(18), Gravity.BOTTOM))
        }
    }

    private fun add(v: View, p: WindowManager.LayoutParams) {
        try {
            wm.addView(v, p)
            views.add(v)
        } catch (e: Exception) {
        }
    }

    fun destroy() {
        for (v in views) try { wm.removeView(v) } catch (e: Exception) { }
        views.clear()
    }
}
