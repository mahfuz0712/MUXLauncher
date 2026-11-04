package com.mux.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherRoot() {
    val ctx = LocalContext.current
    MuxTheme(if (LauncherPrefs.amber) AmberPalette else CyanPalette) {
        var drawer by remember { mutableStateOf(false) }
        var focusSearch by remember { mutableStateOf(false) }
        var settings by remember { mutableStateOf(false) }
        var menuApp by remember { mutableStateOf<AppEntry?>(null) }
        var splitFirst by remember { mutableStateOf<AppEntry?>(null) }
        var update by remember { mutableStateOf<UpdateInfo?>(null) }
        val pager = rememberPagerState(initialPage = 1) { 2 }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) { if (isOnline(ctx)) update = checkUpdate(ctx).second }

        LaunchedEffect(UiBus.tick) {
            when (UiBus.req) {
                "drawer" -> { focusSearch = false; drawer = true }
                "search" -> { focusSearch = true; drawer = true }
                "settings" -> settings = true
                "home" -> {
                    drawer = false; settings = false; menuApp = null; splitFirst = null
                    pager.animateScrollToPage(1)
                }
            }
        }

        // Back never leaves the home screen. It just returns to the main page.
        BackHandler(true) { scope.launch { pager.animateScrollToPage(1) } }

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
                onMenu = { menuApp = it }
            )

            AnimatedVisibility(
                visible = drawer,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut()
            ) {
                DrawerScreen(
                    focusSearch = focusSearch,
                    onClose = { drawer = false },
                    onOpen = { Launch.open(ctx, it); drawer = false },
                    onMenu = { menuApp = it }
                )
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
        }
    }
}
