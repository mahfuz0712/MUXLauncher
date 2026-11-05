package com.mux.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap

/** Hosts the phone's app widgets (clock, weather, calendar...) inside the home screen. */
object WidgetHost {
    private const val HOST_ID = 0x4D55
    private var host: AppWidgetHost? = null
    private var mgr: AppWidgetManager? = null

    fun init(ctx: Context) {
        if (host != null) return
        val app = ctx.applicationContext
        mgr = AppWidgetManager.getInstance(app)
        host = AppWidgetHost(app, HOST_ID)
    }

    fun manager(): AppWidgetManager? = mgr
    fun host(): AppWidgetHost? = host

    fun start() {
        try { host?.startListening() } catch (e: Exception) { }
    }

    fun stop() {
        try { host?.stopListening() } catch (e: Exception) { }
    }

    fun allocate(): Int = try { host?.allocateAppWidgetId() ?: -1 } catch (e: Exception) { -1 }

    fun delete(id: Int) {
        if (id < 0) return
        try { host?.deleteAppWidgetId(id) } catch (e: Exception) { }
    }

    fun providers(): List<AppWidgetProviderInfo> = try {
        mgr?.installedProviders ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun info(id: Int): AppWidgetProviderInfo? = try { mgr?.getAppWidgetInfo(id) } catch (e: Exception) { null }

    /** How many grid cells a widget needs, from its minimum size. */
    fun span(info: AppWidgetProviderInfo): Pair<Int, Int> {
        val cw = if (Geo.gridSize.width > 0f) Geo.gridSize.width / Grid.cols else 1f
        val ch = if (Geo.gridSize.height > 0f) Geo.gridSize.height / Grid.rows else 1f
        val w = Math.ceil((info.minWidth / cw).toDouble()).toInt().coerceIn(1, Grid.cols)
        val h = Math.ceil((info.minHeight / ch).toDouble()).toInt().coerceIn(1, Grid.rows)
        return w to h
    }
}

@Composable
fun WidgetCell(wid: Int, w: Dp, h: Dp, modifier: Modifier = Modifier) {
    val c = Hud.colors
    val info = remember(wid) { WidgetHost.info(wid) }
    val host = WidgetHost.host()
    if (info == null || host == null) {
        Box(modifier.size(w, h).background(c.panel.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            HudText("WIDGET NOT AVAILABLE", color = c.danger, size = 10.sp)
        }
        return
    }
    AndroidView(
        modifier = modifier.size(w, h),
        factory = { ctx ->
            host.createView(ctx, wid, info).also { it.setPadding(0, 0, 0, 0) }
        },
        update = { v ->
            try {
                v.updateAppWidgetSize(null, w.value.toInt(), h.value.toInt(), w.value.toInt(), h.value.toInt())
            } catch (e: Exception) {
            }
        }
    )
}

/** Full-screen list of the widgets installed on the phone. */
@Composable
fun WidgetPicker(onClose: () -> Unit, onPick: (AppWidgetProviderInfo) -> Unit) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val list = remember {
        WidgetHost.providers().sortedBy { (try { it.loadLabel(pm) } catch (e: Exception) { "" }).toString().lowercase() }
    }
    Box(Modifier.fillMaxSize().background(c.bg.copy(alpha = 0.97f))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    HudText("◈ WIDGETS", color = c.primary, size = 16.sp, bold = true)
                    HudText("TAP A WIDGET TO ADD IT TO THIS PAGE", color = c.dim, size = 10.sp)
                }
                HudButton("✕", Modifier.width(46.dp).height(40.dp), KeyKind.ACCENT, fontSize = 16.sp) { onClose() }
            }
            Spacer(Modifier.height(6.dp))
            if (list.isEmpty()) {
                EmptyNote("NO WIDGETS FOUND ON THIS PHONE")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.provider.flattenToString() }) { info ->
                        val label = try { info.loadLabel(pm) } catch (e: Exception) { "?" }
                        val (sw, sh) = WidgetHost.span(info)
                        val pic: ImageBitmap? = remember(info) {
                            try {
                                val d = info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0)
                                d?.toBitmap(160, 100)?.asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        }
                        HudItem(Modifier.fillMaxWidth(), onClick = { onPick(info) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (pic != null) Image(bitmap = pic, contentDescription = null, modifier = Modifier.size(width = 80.dp, height = 50.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    HudText(label, color = c.text, size = 13.sp, bold = true, maxLines = 1)
                                    HudText(info.provider.packageName, color = c.dim, size = 9.sp, maxLines = 1)
                                    HudText("$sw × $sh CELLS", color = c.accent, size = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
