package com.mux.launcher

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One app icon (with optional label). Tap = open.
 * With [src] set it can be dragged (long press, then move). A long press without moving calls [onLong] (the app menu).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppEntry,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    showLabel: Boolean = LauncherPrefs.showLabels,
    src: Src? = null,
    folderId: String = "",
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    val c = Hud.colors
    val haptic = LocalHapticFeedback.current
    val frame = HudShape(10.dp)
    val base = if (src != null) {
        modifier.dragSource('a', app.key, src, folderId, onMenu = onLong).clickable(onClick = onClick)
    } else {
        modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                if (LauncherPrefs.haptic) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLong()
            }
        )
    }
    Column(
        base.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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

/** A folder on the home screen: 2x2 preview of the first apps. */
@Composable
private fun FolderTile(p: Placed, iconSize: Dp, modifier: Modifier, onOpen: () -> Unit) {
    val c = Hud.colors
    val frame = HudShape(10.dp)
    val apps = AppRepo.resolve(p.keys).take(4)
    Column(
        modifier.dragSource('f', p.id, Src.HOME, onMenu = onOpen).clickable(onClick = onOpen).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            Modifier.size(iconSize).clip(frame).background(c.accent.copy(alpha = 0.10f))
                .border(1.dp, c.accent.copy(alpha = 0.7f), frame).padding(4.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            for (row in apps.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (a in row) Image(bitmap = a.icon, contentDescription = null, modifier = Modifier.size(iconSize * 0.4f))
                    if (row.size == 1) Spacer(Modifier.size(iconSize * 0.4f))
                }
            }
        }
        if (LauncherPrefs.showLabels) {
            Spacer(Modifier.height(3.dp))
            HudText(p.name, color = c.accent, size = 10.sp, maxLines = 1, align = TextAlign.Center)
        }
    }
}

/**
 * Home: DASH page + free-grid pages + dock. Icons can be dragged anywhere; pinch in = editor ([onOverview]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeLayer(
    pager: PagerState,
    overview: Boolean,
    onMenu: (AppEntry) -> Unit,
    onFolder: (String) -> Unit,
    onOverview: () -> Unit,
    onExitOverview: () -> Unit
) {
    val ctx = LocalContext.current
    val sc by animateFloatAsState(if (overview) 0.80f else 1f, label = "overview")
    // Scrollable areas swallow drags, so their leftover drag is read here.
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
            .graphicsLayer {
                scaleX = sc
                scaleY = sc
                translationY = -(1f - sc) * size.height * 0.2f
            }
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
            .pointerInput(Unit) {
                // two fingers moving together (pinch in) = home editor. Does not consume anything.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var d0 = 0f
                    var fired = false
                    do {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val pr = ev.changes.filter { it.pressed }
                        if (pr.size >= 2 && !fired) {
                            val d = (pr[0].position - pr[1].position).getDistance()
                            if (d0 == 0f) d0 = d else if (d < d0 * 0.72f) {
                                fired = true
                                onOverview()
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
            TopBar()
            Box(
                Modifier.weight(1f).fillMaxWidth().onGloballyPositioned {
                    Geo.gridTL = it.positionInRoot()
                    Geo.gridSize = androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat())
                }
            ) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    beyondBoundsPageCount = Grid.pages + 1
                ) { page ->
                    if (page == 0) DashPage(onMenu) else GridPage(page - 1, pager, onMenu, onFolder)
                }
            }
            PageDots(pager.currentPage, 1 + Grid.pages)
            Spacer(Modifier.height(4.dp))
            DockBar(onMenu)
        }
        if (overview) {
            // taps anywhere on the shrunk home leave the editor
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onExitOverview() } })
        }
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    val c = Hud.colors
    Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until count) {
            Box(
                Modifier.padding(horizontal = 3.dp).size(width = if (i == current) 18.dp else 6.dp, height = 3.dp)
                    .background(if (i == current) c.primary else c.dim.copy(alpha = 0.6f))
            )
        }
    }
}

@Composable
private fun TopBar() {
    val c = Hud.colors
    val ctx = LocalContext.current
    val now = rememberNow()
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date(now))
    Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            HudText("◈ " + time, color = c.primary, size = 26.sp, bold = true)
            HudText(date.uppercase(), color = c.accent, size = 11.sp)
        }
        HudButton("⌕  SEARCH", Modifier.width(120.dp).height(40.dp), KeyKind.OP, fontSize = 11.sp) {
            Actions.run(ctx, GestureAction.SEARCH)
        }
    }
}

@Composable
private fun GridPage(page: Int, pager: PagerState, onMenu: (AppEntry) -> Unit, onFolder: (String) -> Unit) {
    val ctx = LocalContext.current
    val c = Hud.colors
    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { Actions.run(ctx, LauncherPrefs.gDouble) },
                onLongPress = { Actions.run(ctx, LauncherPrefs.gLong) }
            )
        }
    ) {
        val cols = Grid.cols
        val rows = Grid.rows
        val cw = maxWidth / cols
        val ch = maxHeight / rows
        val iconSize = minOf(cw * 0.62f, ch * 0.52f)

        // the cell under the finger while dragging
        if (Drag.active && pager.currentPage - 1 == page && Geo.gridSize.width > 0f) {
            val cx = ((Drag.pointer.x - Geo.gridTL.x) / (Geo.gridSize.width / cols)).toInt().coerceIn(0, cols - 1)
            val cy = ((Drag.pointer.y - Geo.gridTL.y) / (Geo.gridSize.height / rows)).toInt().coerceIn(0, rows - 1)
            if (Drag.pointer.y >= Geo.gridTL.y) {
                Box(
                    Modifier.offset(cw * cx, ch * cy).size(cw, ch).padding(2.dp)
                        .border(1.dp, c.accent.copy(alpha = 0.6f), HudShape(8.dp))
                        .background(c.accent.copy(alpha = 0.06f), HudShape(8.dp))
                )
            }
        }

        for (p in Grid.items.filter { it.page == page }) {
            androidx.compose.runtime.key(p.kind, p.id) {
                val dragging = Drag.active && Drag.src == Src.HOME && Drag.kind == p.kind && Drag.id == p.id
                Box(
                    Modifier.offset(cw * p.col, ch * p.row).size(cw * p.sw, ch * p.sh).alpha(if (dragging) 0.25f else 1f),
                    contentAlignment = Alignment.Center
                ) {
                    when (p.kind) {
                        'a' -> {
                            val e = AppRepo.byKey(p.id)
                            if (e != null) {
                                AppTile(
                                    e, Modifier.fillMaxSize(), size = iconSize, src = Src.HOME,
                                    onClick = { Launch.open(ctx, e) }, onLong = { onMenu(e) }
                                )
                            }
                        }
                        'f' -> FolderTile(p, iconSize, Modifier.fillMaxSize()) { onFolder(p.id) }
                        else -> Box(
                            Modifier.fillMaxSize().padding(2.dp).dragSource('w', p.id, Src.HOME, onMenu = {})
                        ) {
                            WidgetCell(p.id.toIntOrNull() ?: -1, cw * p.sw - 4.dp, ch * p.sh - 4.dp)
                        }
                    }
                }
            }
        }
        if (Grid.items.none { it.page == page }) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                HudText("EMPTY PAGE · LONG-PRESS AN APP IN THE DRAWER AND DRAG IT HERE", color = c.dim, size = 10.sp, align = TextAlign.Center)
            }
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
    val ctx2 = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { Actions.run(ctx2, LauncherPrefs.gDouble) },
                onLongPress = { Actions.run(ctx2, LauncherPrefs.gLong) }
            )
        }
    ) {
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
            .onGloballyPositioned {
                Geo.dock = androidx.compose.ui.geometry.Rect(it.positionInRoot(), androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()))
            }
            .background(c.panel.copy(alpha = 0.82f), shape)
            .border(1.dp, c.primary.copy(alpha = 0.5f), shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 5) {
            val a = apps.getOrNull(i)
            val dragging = a != null && Drag.active && Drag.src == Src.DOCK && Drag.id == a.key
            Box(Modifier.weight(1f).fillMaxSize().alpha(if (dragging) 0.25f else 1f), contentAlignment = Alignment.Center) {
                if (a != null) {
                    AppTile(a, Modifier.fillMaxSize(), size = 46.dp, showLabel = false, src = Src.DOCK, onClick = { Launch.open(ctx, a) }, onLong = { onMenu(a) })
                } else if (apps.isEmpty() && i == 0) {
                    // hint only once
                }
            }
        }
    }
}
