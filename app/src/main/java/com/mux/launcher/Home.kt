package com.mux.launcher

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One app icon (with optional label). Tap = open, long press = menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppEntry,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    showLabel: Boolean = LauncherPrefs.showLabels,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    val c = Hud.colors
    val haptic = LocalHapticFeedback.current
    val frame = HudShape(10.dp)
    Column(
        modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (LauncherPrefs.haptic) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLong()
                }
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size(size).then(
                if (LauncherPrefs.iconFrame)
                    Modifier.clip(frame)
                        .background(c.panel.copy(alpha = 0.65f))
                        .border(1.dp, c.primary.copy(alpha = 0.45f), frame)
                        .padding(5.dp)
                else Modifier
            )
        )
        if (showLabel) {
            Spacer(Modifier.height(3.dp))
            HudText(app.label, color = c.text, size = 10.sp, maxLines = 1, align = TextAlign.Center)
        }
    }
}

/** Home screen: two pages (DASH, HOME) and the dock. Handles the swipe / tap gestures. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeLayer(pager: PagerState, onMenu: (AppEntry) -> Unit) {
    val ctx = LocalContext.current
    // Scrollable areas (the app grid) swallow drags, so their leftover drag is read here.
    val nested = remember {
        object : NestedScrollConnection {
            var acc = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag) acc += available.y
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (acc < -110f) Actions.run(ctx, LauncherPrefs.gUp)
                else if (acc > 110f) Actions.run(ctx, LauncherPrefs.gDown)
                acc = 0f
                return Velocity.Zero
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .nestedScroll(nested)
            .pointerInput(Unit) {
                var acc = 0f
                detectVerticalDragGestures(
                    onDragStart = { acc = 0f },
                    onVerticalDrag = { _, dy -> acc += dy },
                    onDragEnd = {
                        if (acc < -110f) Actions.run(ctx, LauncherPrefs.gUp)
                        else if (acc > 110f) Actions.run(ctx, LauncherPrefs.gDown)
                        acc = 0f
                    },
                    onDragCancel = { acc = 0f }
                )
            }
    ) {
        // Empty-space gestures live on a layer behind the icons, so icons keep their own taps.
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { Actions.run(ctx, LauncherPrefs.gDouble) },
                    onLongPress = { Actions.run(ctx, LauncherPrefs.gLong) }
                )
            }
        )
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                if (page == 0) DashPage(onMenu) else HomePage(onMenu)
            }
            PageDots(pager.currentPage)
            Spacer(Modifier.height(4.dp))
            DockBar(onMenu)
        }
    }
}

@Composable
private fun PageDots(current: Int) {
    val c = Hud.colors
    Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..1) {
            Box(
                Modifier.padding(horizontal = 3.dp).size(width = if (i == current) 18.dp else 6.dp, height = 3.dp)
                    .background(if (i == current) c.primary else c.dim.copy(alpha = 0.6f))
            )
        }
    }
}

@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return now
}

@Composable
private fun ClockBlock() {
    val c = Hud.colors
    val now = rememberNow()
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(now))
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp)) {
        HudText("◈ " + time, color = c.primary, size = 52.sp, bold = true)
        HudText(date.uppercase(), color = c.accent, size = 13.sp)
    }
}

@Composable
private fun SearchBarButton() {
    val c = Hud.colors
    val ctx = LocalContext.current
    val shape = HudShape(8.dp)
    Row(
        Modifier.fillMaxWidth().height(44.dp)
            .background(c.primary.copy(alpha = 0.06f), shape)
            .border(1.dp, c.primary.copy(alpha = 0.5f), shape)
            .clickable { Actions.run(ctx, GestureAction.SEARCH) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HudText("⌕  SEARCH APPS", color = c.dim, size = 13.sp)
    }
}

@Composable
private fun HomePage(onMenu: (AppEntry) -> Unit) {
    val ctx = LocalContext.current
    val apps = AppRepo.resolve(LauncherPrefs.homeApps)
    Column(Modifier.fillMaxSize()) {
        ClockBlock()
        SearchBarButton()
        Spacer(Modifier.height(8.dp))
        if (apps.isEmpty()) {
            EmptyNote("HOME IS EMPTY. SWIPE UP FOR ALL APPS, LONG-PRESS AN APP > ADD TO HOME.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(LauncherPrefs.homeCols.coerceIn(3, 6)),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(apps, key = { it.key }) { a ->
                    AppTile(a, Modifier.fillMaxWidth(), onClick = { Launch.open(ctx, a) }, onLong = { onMenu(a) })
                }
            }
        }
    }
}

@Composable
private fun rememberBattery(): Int {
    val ctx = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i == null) return
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0 && s > 0) level = l * 100 / s
            }
        }
        try { ctx.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (e: Exception) { }
        onDispose { try { ctx.unregisterReceiver(r) } catch (e: Exception) { } }
    }
    return level
}

@Composable
private fun DashPage(onMenu: (AppEntry) -> Unit) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val battery = rememberBattery()
    val stat = try {
        val s = StatFs(Environment.getDataDirectory().path)
        val total = s.totalBytes
        val free = s.availableBytes
        ((total - free) * 100 / total).toInt() to (free / 1_000_000_000L)
    } catch (e: Exception) {
        0 to 0L
    }
    val ram = try {
        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        ((mi.totalMem - mi.availMem) * 100 / mi.totalMem).toInt()
    } catch (e: Exception) {
        0
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ClockBlock()
        HudPanel(Modifier.fillMaxWidth(), "SYSTEM STATUS") {
            StatRow("BATTERY", if (battery >= 0) "$battery%" else "--", if (battery in 0..20) c.danger else c.good)
            StatRow("STORAGE", "${stat.first}% USED  ·  ${stat.second} GB FREE", c.primary)
            StatRow("MEMORY", "$ram% IN USE", c.primary)
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "QUICK ACTIONS") {
            val acts = listOf(
                GestureAction.TORCH, GestureAction.SHOT, GestureAction.LOCK, GestureAction.SPLIT,
                GestureAction.RECENTS, GestureAction.QUICK, GestureAction.DOCK, GestureAction.SETTINGS
            )
            for (row in acts.chunked(4)) {
                Row(Modifier.fillMaxWidth().height(46.dp)) {
                    for (a in row) {
                        HudButton(a.label, Modifier.weight(1f).height(46.dp), KeyKind.OP, fontSize = 9.sp) { Actions.run(ctx, a) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val favs = AppRepo.resolve(LauncherPrefs.favorites)
        HudPanel(Modifier.fillMaxWidth(), "FAVORITES") {
            if (favs.isEmpty()) {
                HudText("LONG-PRESS AN APP > FAVORITE", color = c.dim, size = 11.sp)
            } else {
                for (row in favs.chunked(4)) {
                    Row(Modifier.fillMaxWidth()) {
                        for (a in row) {
                            AppTile(a, Modifier.weight(1f), onClick = { Launch.open(ctx, a) }, onLong = { onMenu(a) })
                        }
                        for (i in row.size until 4) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        HudText(label, color = Hud.colors.dim, size = 11.sp)
        HudText(value, color = color, size = 13.sp, bold = true)
    }
}

@Composable
private fun DockBar(onMenu: (AppEntry) -> Unit) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val apps = AppRepo.resolve(LauncherPrefs.dock)
    val shape = HudShape(12.dp)
    Row(
        Modifier.fillMaxWidth().height(70.dp)
            .background(c.panel.copy(alpha = 0.82f), shape)
            .border(1.dp, c.primary.copy(alpha = 0.5f), shape)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (apps.isEmpty()) {
            HudText("DOCK EMPTY: LONG-PRESS AN APP > ADD TO DOCK", color = c.dim, size = 10.sp)
        }
        for (a in apps) {
            AppTile(a, Modifier.weight(1f), size = 48.dp, showLabel = false, onClick = { Launch.open(ctx, a) }, onLong = { onMenu(a) })
        }
    }
}
