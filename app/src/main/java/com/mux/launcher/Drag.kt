@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mux.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

enum class Src { HOME, DOCK, DRAWER, FOLDER }

/** Where things are on screen (window coordinates). Filled in by the home screen while it is shown. */
object Geo {
    var gridTL: Offset = Offset.Zero
    var gridSize: Size = Size.Zero
    var dock: Rect = Rect.Zero
    var zones: Rect = Rect.Zero
    var screenW: Float = 0f
    var density: Float = 1f
    var pager: PagerState? = null
    var ctx: Context? = null

    val grid: Rect get() = Rect(gridTL, gridSize)
}

/** The thing being dragged right now (an app, a folder or a widget). */
object Drag {
    var active by mutableStateOf(false)
        private set
    var kind by mutableStateOf('a')
        private set
    var id by mutableStateOf("")
        private set
    var src by mutableStateOf(Src.HOME)
        private set
    var folderId by mutableStateOf("")
        private set
    var pointer by mutableStateOf(Offset.Zero)

    /** Bumped when a drag ends, so the screen can close the drawer / folder it was dragged from. */
    var endTick by mutableIntStateOf(0)
        private set
    var endedFrom: Src = Src.HOME
        private set
    var endedMoved = false
        private set

    fun begin(k: Char, i: String, s: Src, folder: String, at: Offset) {
        kind = k; id = i; src = s; folderId = folder; pointer = at; active = true
    }

    private fun end(moved: Boolean) {
        endedFrom = src
        endedMoved = moved
        active = false
        endTick += 1
    }

    fun cancel() = end(false)

    fun finish() {
        val ctx = Geo.ctx
        try {
            if (ctx != null) Dropper.drop(ctx)
        } catch (e: Exception) {
        }
        Grid.prune()
        end(true)
    }

    /** 0 = remove / uninstall, 1 = edge dock, 2 = app info, -1 = not over the zone bar. */
    fun zoneAt(p: Offset): Int {
        val z = Geo.zones
        if (z.width <= 0f || !z.contains(p)) return -1
        if (kind == 'w') return 0
        return ((p.x - z.left) / z.width * 3f).toInt().coerceIn(0, 2)
    }
}

/**
 * Long press, then drag. A short touch is left to the normal tap / scroll handling.
 * A long press that is released without moving calls [onMenu] (the old long-press menu).
 */
@Composable
fun Modifier.dragSource(
    kind: Char,
    id: String,
    src: Src,
    folderId: String = "",
    onMenu: () -> Unit
): Modifier {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val haptic = LocalHapticFeedback.current
    val menu by androidx.compose.runtime.rememberUpdatedState(onMenu)
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(kind, id, src, folderId) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val slop = viewConfiguration.touchSlop
                val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var done = false
                    while (!done) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id }
                        if (ch == null || !ch.pressed || (ch.position - down.position).getDistance() > slop) done = true
                    }
                    true
                }
                if (early != null) return@awaitEachGesture

                if (LauncherPrefs.haptic) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                Drag.begin(kind, id, src, folderId, origin + down.position)
                var moved = false
                try {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        ch.consume()
                        if (!ch.pressed) break
                        if (!moved && (ch.position - down.position).getDistance() > slop * 1.5f) moved = true
                        Drag.pointer = origin + ch.position
                    }
                    if (moved) Drag.finish() else {
                        Drag.cancel()
                        menu()
                    }
                } finally {
                    if (Drag.active) Drag.cancel()
                }
            }
        }
}

/** What happens when something is dropped. */
object Dropper {
    private fun toast(ctx: Context, t: String) = Util.toast(ctx, t)

    fun drop(ctx: Context) {
        val p = Drag.pointer
        val z = Drag.zoneAt(p)
        if (z >= 0) {
            zone(ctx, z)
            return
        }
        if (Drag.kind == 'a' && Geo.dock.width > 0f && Geo.dock.contains(p)) {
            toDock(ctx, p)
            return
        }
        if (Geo.grid.contains(p)) toGrid(ctx, p)
    }

    private fun zone(ctx: Context, z: Int) {
        val id = Drag.id
        when (z) {
            0 -> when {
                Drag.kind == 'w' -> {
                    WidgetHost.delete(id.toIntOrNull() ?: -1)
                    Grid.remove('w', id)
                }
                Drag.src == Src.DRAWER -> {
                    val app = AppRepo.byKey(id)
                    if (app != null && !app.system) {
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                        }
                    } else toast(ctx, "SYSTEM APP: CANNOT UNINSTALL")
                }
                Drag.src == Src.DOCK -> {
                    LauncherPrefs.dock = LauncherPrefs.dock - id
                    LauncherPrefs.commit(false)
                }
                Drag.src == Src.FOLDER -> Grid.removeFromFolder(Drag.folderId, id)
                else -> Grid.remove(Drag.kind, id)
            }
            1 -> if (Drag.kind == 'a') {
                if (LauncherPrefs.edgeApps.contains(id)) toast(ctx, "ALREADY IN EDGE DOCK")
                else if (LauncherPrefs.edgeApps.size >= 12) toast(ctx, "EDGE DOCK IS FULL (12)")
                else {
                    LauncherPrefs.edgeApps = LauncherPrefs.edgeApps + id
                    LauncherPrefs.commit(false)
                    toast(ctx, "ADDED TO EDGE DOCK")
                }
            }
            2 -> if (Drag.kind == 'a') {
                val app = AppRepo.byKey(id)
                if (app != null) {
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }

    private fun leaveSource() {
        when (Drag.src) {
            Src.DOCK -> {
                LauncherPrefs.dock = LauncherPrefs.dock - Drag.id
                LauncherPrefs.commit(false)
            }
            Src.FOLDER -> Grid.removeFromFolder(Drag.folderId, Drag.id)
            else -> {}
        }
    }

    private fun toDock(ctx: Context, p: Offset) {
        val id = Drag.id
        val list = LauncherPrefs.dock.toMutableList()
        val slot = Geo.dock.width / 5f
        val idx = ((p.x - Geo.dock.left) / slot).toInt().coerceIn(0, 4)
        if (Drag.src == Src.DOCK) {
            list.remove(id)
        } else {
            if (list.contains(id)) { toast(ctx, "ALREADY IN DOCK"); return }
            if (list.size >= 5) { toast(ctx, "DOCK IS FULL (5)"); return }
            if (Drag.src == Src.FOLDER) Grid.removeFromFolder(Drag.folderId, id)
        }
        list.add(idx.coerceAtMost(list.size), id)
        LauncherPrefs.dock = list
        LauncherPrefs.commit(false)
    }

    private fun toGrid(ctx: Context, p: Offset) {
        val page = (Geo.pager?.currentPage ?: 1) - 1
        if (page < 0) { toast(ctx, "SWIPE TO A HOME PAGE FIRST"); return }
        val cw = Geo.gridSize.width / Grid.cols
        val ch = Geo.gridSize.height / Grid.rows
        var col = ((p.x - Geo.gridTL.x) / cw).toInt().coerceIn(0, Grid.cols - 1)
        var row = ((p.y - Geo.gridTL.y) / ch).toInt().coerceIn(0, Grid.rows - 1)
        val id = Drag.id
        when (Drag.kind) {
            'w' -> {
                val w = Grid.find('w', id) ?: return
                col = col.coerceAtMost(Grid.cols - w.sw)
                row = row.coerceAtMost(Grid.rows - w.sh)
                if (Grid.areaFree(page, row, col, w.sw, w.sh, w)) Grid.move(w, page, row, col) else toast(ctx, "NO SPACE THERE")
            }
            'f' -> {
                val f = Grid.find('f', id) ?: return
                if (Grid.itemAt(page, row, col, f) == null) Grid.move(f, page, row, col) else toast(ctx, "THAT SPOT IS TAKEN")
            }
            else -> dropApp(ctx, id, page, row, col)
        }
    }

    private fun dropApp(ctx: Context, key: String, page: Int, row: Int, col: Int) {
        val self = if (Drag.src == Src.HOME) Grid.find('a', key) else null
        val t = Grid.itemAt(page, row, col, self)
        when {
            t == null -> {
                leaveSource()
                val ex = Grid.find('a', key)
                if (ex != null) Grid.move(ex, page, row, col) else Grid.addAt(key, page, row, col)
            }
            t.kind == 'a' && t.id == key -> {}
            t.kind == 'a' -> {
                leaveSource()
                Grid.makeFolder(t, key)
            }
            t.kind == 'f' -> {
                if (Drag.src == Src.FOLDER && Drag.folderId == t.id) return
                leaveSource()
                Grid.addToFolder(t.id, key)
            }
            else -> toast(ctx, "A WIDGET IS THERE")
        }
    }
}

/** Drag ghost + the REMOVE / EDGE DOCK / APP INFO bar. Draw this on top of everything. */
@Composable
fun DragLayer() {
    val d = LocalDensity.current
    Geo.density = d.density

    LaunchedEffect(Drag.active) {
        if (!Drag.active) return@LaunchedEffect
        var hold = 0
        while (Drag.active) {
            delay(110)
            val x = Drag.pointer.x
            val edge = 56f * Geo.density
            val dir = if (x < edge) -1 else if (x > Geo.screenW - edge) 1 else 0
            if (dir == 0 || Geo.zones.contains(Drag.pointer)) {
                hold = 0
            } else {
                hold++
                if (hold >= 6) {
                    hold = 0
                    val pg = Geo.pager
                    if (pg != null) {
                        val target = pg.currentPage + dir
                        if (target > Grid.pages) {
                            val lastHasItems = Grid.items.any { it.page == Grid.pages - 1 && !(it.kind == Drag.kind && it.id == Drag.id) }
                            if (lastHasItems) {
                                Grid.addPage()
                                delay(120)
                                pg.animateScrollToPage(target)
                            }
                        } else if (target >= 1) {
                            pg.animateScrollToPage(target)
                        }
                    }
                }
            }
        }
    }

    if (!Drag.active) return
    val c = Hud.colors
    val hover = Drag.zoneAt(Drag.pointer)
    val labels: List<String> = when {
        Drag.kind == 'w' -> listOf("✕ REMOVE WIDGET")
        Drag.src == Src.DRAWER -> listOf("✕ UNINSTALL", "▣ EDGE DOCK", "ⓘ APP INFO")
        Drag.kind == 'f' -> listOf("✕ REMOVE FOLDER")
        else -> listOf("✕ REMOVE", "▣ EDGE DOCK", "ⓘ APP INFO")
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().height(52.dp)
                    .background(c.bg.copy(alpha = 0.92f))
                    .border(1.dp, c.primary.copy(alpha = 0.6f))
                    .onGloballyPositioned { Geo.zones = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat())) }
            ) {
                labels.forEachIndexed { i, t ->
                    val on = hover == i
                    val col = if (i == 0) c.danger else if (i == 1) c.accent else c.primary
                    Box(
                        Modifier.weight(1f).fillMaxSize().background(if (on) col.copy(alpha = 0.35f) else col.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        HudText(t, color = col, size = 11.sp, bold = on, align = TextAlign.Center)
                    }
                }
            }
        }
        val half = 30.dp
        val hp = with(d) { half.toPx() }
        Box(
            Modifier.offset { IntOffset((Drag.pointer.x - hp).roundToInt(), (Drag.pointer.y - hp).roundToInt()) }.size(half * 2),
            contentAlignment = Alignment.Center
        ) {
            when (Drag.kind) {
                'a' -> {
                    val app = AppRepo.byKey(Drag.id)
                    if (app != null) {
                        Image(
                            bitmap = app.icon, contentDescription = null,
                            modifier = Modifier.size(56.dp).border(1.dp, c.accent, HudShape(10.dp)).background(c.panel.copy(alpha = 0.9f), HudShape(10.dp))
                        )
                    }
                }
                'f' -> Box(
                    Modifier.size(56.dp).border(1.dp, c.accent, HudShape(10.dp)).background(c.panel.copy(alpha = 0.9f), HudShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) { HudText("▤", color = c.accent, size = 26.sp) }
                else -> Box(
                    Modifier.size(64.dp).border(1.dp, c.accent).background(c.panel.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) { HudText("◈", color = c.accent, size = 22.sp) }
            }
        }
    }
}
