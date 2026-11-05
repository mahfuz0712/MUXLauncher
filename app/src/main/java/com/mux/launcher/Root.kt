package com.mux.launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun LauncherRoot() {
    val ctx = LocalContext.current
    MuxTheme(if (LauncherPrefs.amber) AmberPalette else CyanPalette) {
        val c = Hud.colors
        var drawer by remember { mutableStateOf(false) }
        var focusSearch by remember { mutableStateOf(false) }
        var settings by remember { mutableStateOf(false) }
        var overview by remember { mutableStateOf(false) }
        var widgets by remember { mutableStateOf(false) }
        var folderId by remember { mutableStateOf<String?>(null) }
        var menuApp by remember { mutableStateOf<AppEntry?>(null) }
        var splitFirst by remember { mutableStateOf<AppEntry?>(null) }
        var update by remember { mutableStateOf<UpdateInfo?>(null) }
        val pager = rememberPagerState(initialPage = 1) { 1 + Grid.pages }
        val scope = rememberCoroutineScope()
        val screenW = LocalConfiguration.current.screenWidthDp

        val dens = LocalDensity.current.density
        Geo.pager = pager
        Geo.ctx = ctx.applicationContext
        Geo.density = dens
        Geo.screenW = screenW * dens

        LaunchedEffect(Unit) { if (isOnline(ctx)) update = checkUpdate(ctx).second }

        // ---- adding a widget: bind -> (configure) -> place ----
        var pendingId by remember { mutableStateOf(-1) }
        var pendingInfo by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }
        fun place(id: Int, info: AppWidgetProviderInfo) {
            val (sw, sh) = WidgetHost.span(info)
            Grid.addWidget(id, info.provider.flattenToString(), sw, sh, (pager.currentPage - 1).coerceAtLeast(0))
            widgets = false
            overview = false
            Util.toast(ctx, "WIDGET ADDED")
        }
        val configLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val info = pendingInfo
            if (r.resultCode == Activity.RESULT_OK && info != null) place(pendingId, info)
            else WidgetHost.delete(pendingId)
            pendingInfo = null
        }
        fun configureOrPlace(id: Int, info: AppWidgetProviderInfo) {
            val cfg = info.configure
            if (cfg != null) {
                pendingId = id
                pendingInfo = info
                try {
                    configLauncher.launch(
                        Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(cfg)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    )
                } catch (e: Exception) {
                    place(id, info)
                }
            } else {
                place(id, info)
            }
        }
        val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val info = pendingInfo
            if (r.resultCode == Activity.RESULT_OK && info != null) configureOrPlace(pendingId, info)
            else {
                WidgetHost.delete(pendingId)
                pendingInfo = null
            }
        }
        fun addWidget(info: AppWidgetProviderInfo) {
            val id = WidgetHost.allocate()
            if (id < 0) {
                Util.toast(ctx, "CANNOT ADD WIDGETS ON THIS PHONE")
                return
            }
            val ok = try { WidgetHost.manager()?.bindAppWidgetIdIfAllowed(id, info.provider) ?: false } catch (e: Exception) { false }
            if (ok) {
                configureOrPlace(id, info)
            } else {
                pendingId = id
                pendingInfo = info
                try {
                    bindLauncher.launch(
                        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    )
                } catch (e: Exception) {
                    WidgetHost.delete(id)
                    Util.toast(ctx, "THIS PHONE BLOCKED THE WIDGET")
                }
            }
        }

        LaunchedEffect(UiBus.tick) {
            when (UiBus.req) {
                "drawer" -> { focusSearch = false; drawer = true }
                "search" -> { focusSearch = true; drawer = true }
                "settings" -> settings = true
                "overview" -> { drawer = false; overview = true; pager.animateScrollToPage(1.coerceAtMost(pager.pageCount - 1)) }
                "home" -> {
                    drawer = false; settings = false; menuApp = null; splitFirst = null
                    overview = false; widgets = false; folderId = null
                    pager.animateScrollToPage(1)
                }
            }
        }

        // a drag that started in the drawer or in a folder is over: close that window
        LaunchedEffect(Drag.endTick) {
            if (Drag.endTick > 0 && Drag.endedMoved) {
                if (Drag.endedFrom == Src.DRAWER) drawer = false
                if (Drag.endedFrom == Src.FOLDER) folderId = null
            }
        }

        // Back never leaves the home screen. It closes the editor / folder, then returns to the main page.
        BackHandler(true) {
            if (overview) { overview = false; Grid.prune() }
            else if (folderId != null) folderId = null
            else scope.launch { pager.animateScrollToPage(1) }
        }

        update?.let { u ->
            UpdateDialog(
                u, appVersion(ctx),
                onUpdate = { Util.open(ctx, u.apkUrl); update = null },
                onCancel = { update = null }
            )
        }

        Box(Modifier.fillMaxSize()) {
            if (LauncherPrefs.wallpaper) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f)))
            } else {
                HudBackground(Modifier.fillMaxSize()) {}
            }

            HomeLayer(
                pager = pager,
                overview = overview,
                onMenu = { menuApp = it },
                onFolder = { folderId = it },
                onOverview = { overview = true },
                onExitOverview = { overview = false; Grid.prune() }
            )

            // editor: WIDGETS / WALLPAPER / SETTINGS
            AnimatedVisibility(
                visible = overview,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                HudPanel(Modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp).pointerInput(Unit) { detectTapGestures { } }, "HOME EDITOR") {
                    Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HudButton("▣\nWIDGETS", Modifier.weight(1f).height(58.dp), KeyKind.ACCENT, fontSize = 11.sp) { widgets = true }
                        HudButton("▤\nWALLPAPER", Modifier.weight(1f).height(58.dp), KeyKind.ACCENT, fontSize = 11.sp) {
                            LauncherPrefs.wallpaper = true
                            LauncherPrefs.commit()
                            overview = false
                            try {
                                ctx.startActivity(
                                    Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "WALLPAPER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (e: Exception) {
                                Util.toast(ctx, "NO WALLPAPER PICKER FOUND")
                            }
                        }
                        HudButton("⚙\nSETTINGS", Modifier.weight(1f).height(58.dp), KeyKind.ACCENT, fontSize = 11.sp) {
                            overview = false
                            settings = true
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HudButton("+ PAGE", Modifier.weight(1f).height(40.dp), KeyKind.OP, fontSize = 11.sp) {
                            Grid.addPage()
                            scope.launch { pager.animateScrollToPage(Grid.pages) }
                        }
                        HudButton("DONE", Modifier.weight(1f).height(40.dp), KeyKind.OP, fontSize = 11.sp) {
                            overview = false
                            Grid.prune()
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = drawer,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut()
            ) {
                val hide = Drag.active && Drag.src == Src.DRAWER
                Box(Modifier.fillMaxSize().alpha(if (hide) 0f else 1f)) {
                    DrawerScreen(
                        focusSearch = focusSearch,
                        onClose = { drawer = false },
                        onOpen = { Launch.open(ctx, it); drawer = false },
                        onMenu = { menuApp = it }
                    )
                }
            }

            folderId?.let { fid ->
                FolderOverlay(
                    folderId = fid,
                    hidden = Drag.active && Drag.src == Src.FOLDER,
                    onClose = { folderId = null },
                    onMenu = { menuApp = it }
                )
            }

            if (widgets) {
                WidgetPicker(onClose = { widgets = false }, onPick = { addWidget(it) })
            }

            AnimatedVisibility(
                visible = settings,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 }
            ) {
                SettingsHost(onClose = { settings = false }, onUpdate = { update = it })
            }

            menuApp?.let { a ->
                AppMenuDialog(
                    app = a,
                    page = (pager.currentPage - 1).coerceAtLeast(0),
                    onDismiss = { menuApp = null },
                    onSplit = { splitFirst = it; menuApp = null }
                )
            }

            splitFirst?.let { a ->
                AppPickerDialog(
                    title = "SPLIT: " + a.label.uppercase() + " + ?",
                    onPick = { b -> Launch.splitPair(ctx, a, b); splitFirst = null; drawer = false },
                    onDismiss = { splitFirst = null }
                )
            }

            DragLayer()
        }
    }
}

/** Contents of a folder. Tap an app to open it, long-press and drag to move it out. */
@Composable
fun FolderOverlay(folderId: String, hidden: Boolean, onClose: () -> Unit, onMenu: (AppEntry) -> Unit) {
    val ctx = LocalContext.current
    val f = Grid.find('f', folderId)
    if (f == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    var name by remember(f.id) { mutableStateOf(f.name) }
    val close = {
        Grid.renameFolder(f.id, name)
        onClose()
    }
    Box(
        Modifier.fillMaxSize().alpha(if (hidden) 0f else 1f)
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) { detectTapGestures { close() } },
        contentAlignment = Alignment.Center
    ) {
        HudPanel(Modifier.padding(20.dp).fillMaxWidth().pointerInput(Unit) { detectTapGestures { } }, "FOLDER") {
            HudField(name, "FOLDER NAME", Modifier.fillMaxWidth().height(42.dp), onDone = { Grid.renameFolder(f.id, name) }) { name = it }
            Spacer(Modifier.height(8.dp))
            for (row in AppRepo.resolve(f.keys).chunked(4)) {
                Row(Modifier.fillMaxWidth()) {
                    for (a in row) {
                        AppTile(
                            a, Modifier.weight(1f), src = Src.FOLDER, folderId = f.id,
                            onClick = { Launch.open(ctx, a); close() }, onLong = { onMenu(a) }
                        )
                    }
                    for (i in row.size until 4) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            HudText("LONG-PRESS AN APP AND DRAG IT OUT OF THE FOLDER", color = Hud.colors.dim, size = 9.sp)
        }
    }
}
