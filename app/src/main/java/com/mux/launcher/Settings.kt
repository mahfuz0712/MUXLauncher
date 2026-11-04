package com.mux.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SettingsTab(val label: String, val title: String) {
    SETUP("SETUP", "SETUP"),
    HOME("HOME", "HOME & DRAWER"),
    GESTURE("GESTURE", "GESTURES"),
    DOCK("DOCK", "DOCK & WINDOWS"),
    SYS("SYS", "SYSTEM")
}

@Composable
fun SettingsHost(onClose: () -> Unit, onUpdate: (UpdateInfo) -> Unit) {
    val c = Hud.colors
    BackHandler(true) { onClose() }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val current = SettingsTab.values()[tabIndex]
    HudBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
            Row(
                Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    HudText("◈ MUX LAUNCHER", color = c.primary, size = 16.sp, bold = true)
                    HudText(current.title, color = c.dim, size = 10.sp)
                }
                HudButton("◂ HOME", Modifier.width(96.dp).height(36.dp), KeyKind.ACCENT, fontSize = 11.sp) { onClose() }
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (current) {
                    SettingsTab.SETUP -> SetupScreen()
                    SettingsTab.HOME -> LayoutScreen()
                    SettingsTab.GESTURE -> GestureScreen()
                    SettingsTab.DOCK -> DockScreen()
                    SettingsTab.SYS -> SystemScreen(false) { onUpdate(it) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().height(46.dp)) {
                for (t in SettingsTab.values()) {
                    HudButton(
                        label = t.label,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        kind = KeyKind.OP,
                        selected = t == current,
                        fontSize = 10.sp
                    ) { tabIndex = t.ordinal }
                }
            }
        }
    }
}

private fun go(ctx: Context, action: String) {
    try {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Util.toast(ctx, "CANNOT OPEN THIS SETTING")
    }
}

private fun isDefaultHome(ctx: Context): Boolean = try {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == ctx.packageName
} catch (e: Exception) {
    false
}

fun isServiceEnabled(ctx: Context): Boolean {
    if (MuxService.instance != null) return true
    val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return s.contains(ctx.packageName + "/") && s.contains("MuxService")
}

private fun onOff(b: Boolean) = if (b) "ON" else "OFF"

@Composable
private fun StatusLine(ok: Boolean, onText: String, offText: String) {
    val c = Hud.colors
    HudText(if (ok) "● $onText" else "○ $offText", color = if (ok) c.good else c.danger, size = 13.sp, bold = true)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Hint(text: String) {
    HudText(text, color = Hud.colors.dim, size = 10.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Btn(label: String, kind: KeyKind = KeyKind.OP, onClick: () -> Unit) {
    HudButton(label, Modifier.fillMaxWidth().height(42.dp), kind, fontSize = 12.sp, onClick = onClick)
}

@Composable
private fun ScreenColumn(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        content()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun SetupScreen() {
    val ctx = LocalContext.current
    val tick = resumeTick.intValue
    val home = remember(tick) { isDefaultHome(ctx) }
    val svc = remember(tick) { isServiceEnabled(ctx) }
    ScreenColumn {
        HudPanel(Modifier.fillMaxWidth(), "1 · DEFAULT HOME APP") {
            StatusLine(home, "MUX LAUNCHER IS YOUR HOME APP", "NOT THE DEFAULT HOME APP YET")
            Btn("SET AS DEFAULT HOME", KeyKind.ACCENT) {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    go(ctx, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                }
            }
            Hint("Choose MUX Launcher in the list. On Honor you can also use Settings > Apps > Default apps > Home screen.")
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "2 · MUX SERVICE (ACCESSIBILITY)") {
            StatusLine(svc, "SERVICE IS RUNNING", "SERVICE IS OFF")
            Btn("OPEN ACCESSIBILITY SETTINGS", KeyKind.ACCENT) { go(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS) }
            Btn("ALLOW RESTRICTED SETTINGS (APP INFO)", KeyKind.FN) { Util.openAppSettings(ctx) }
            Hint("Turn on MUX Launcher under Installed / Downloaded apps. If it is greyed out (Android 13+ for apps outside the Play Store): App info > ⋮ menu > Allow restricted settings, then try again.")
            Hint("This service powers: back / recents / notifications / lock / screenshot / split screen, the edge dock and the window bar. It only sees which app is in front, never your screen text.")
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "3 · KEEP IT ALIVE") {
            Btn("BATTERY OPTIMIZATION", KeyKind.OP) { go(ctx, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
            Hint("Honor: Settings > Battery > App launch > MUX Launcher > Manage manually > turn on all three switches. Otherwise the system may stop the service.")
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "4 · FLOATING WINDOWS (OPTIONAL)") {
            Btn("DEVELOPER OPTIONS", KeyKind.FN) { go(ctx, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }
            Hint("FLOAT mode needs a phone that allows freeform windows. In Developer options turn on 'Enable freeform windows' and 'Force activities to be resizable', then restart. Without it FLOAT opens the app normally.")
        }
    }
}

@Composable
fun LayoutScreen() {
    val ctx = LocalContext.current
    ScreenColumn {
        HudPanel(Modifier.fillMaxWidth(), "LOOK") {
            SettingRow("PALETTE", "cyan or amber HUD colors", if (LauncherPrefs.amber) "AMBER" else "CYAN") {
                LauncherPrefs.amber = !LauncherPrefs.amber; LauncherPrefs.commit()
            }
            SettingRow("WALLPAPER", "show your phone wallpaper behind the home screen", onOff(LauncherPrefs.wallpaper)) {
                LauncherPrefs.wallpaper = !LauncherPrefs.wallpaper; LauncherPrefs.commit()
            }
            SettingRow("ICON FRAME", "HUD frame around every icon", onOff(LauncherPrefs.iconFrame)) {
                LauncherPrefs.iconFrame = !LauncherPrefs.iconFrame; LauncherPrefs.commit()
            }
            SettingRow("LABELS", "app names under icons", onOff(LauncherPrefs.showLabels)) {
                LauncherPrefs.showLabels = !LauncherPrefs.showLabels; LauncherPrefs.commit()
            }
            SettingRow("HOME COLUMNS", "icons per row on home", LauncherPrefs.homeCols.toString()) {
                LauncherPrefs.homeCols = if (LauncherPrefs.homeCols >= 6) 3 else LauncherPrefs.homeCols + 1; LauncherPrefs.commit()
            }
            SettingRow("DRAWER COLUMNS", "icons per row in the app drawer", LauncherPrefs.drawerCols.toString()) {
                LauncherPrefs.drawerCols = if (LauncherPrefs.drawerCols >= 6) 3 else LauncherPrefs.drawerCols + 1; LauncherPrefs.commit()
            }
            SettingRow("AUTO KEYBOARD", "open the keyboard when the drawer opens", onOff(LauncherPrefs.autoKeyboard)) {
                LauncherPrefs.autoKeyboard = !LauncherPrefs.autoKeyboard; LauncherPrefs.commit()
            }
            SettingRow("HAPTIC", "vibrate on long press", onOff(LauncherPrefs.haptic)) {
                LauncherPrefs.haptic = !LauncherPrefs.haptic; LauncherPrefs.commit()
            }
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "HIDDEN APPS") {
            val hidden = AppRepo.resolve(LauncherPrefs.hidden)
            if (hidden.isEmpty()) HudText("NONE. LONG-PRESS AN APP > HIDE APP", color = Hud.colors.dim, size = 11.sp)
            for (a in hidden) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(bitmap = a.icon, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    HudText(a.label, Modifier.weight(1f), size = 12.sp, maxLines = 1)
                    HudButton("UNHIDE", Modifier.width(86.dp).height(34.dp), KeyKind.OP, fontSize = 10.sp) {
                        LauncherPrefs.hidden = LauncherPrefs.hidden - a.key; LauncherPrefs.commit()
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "RESET") {
            Btn("RESET HOME, DOCK AND EDGE DOCK APPS", KeyKind.DANGER) {
                LauncherPrefs.dock = emptyList()
                LauncherPrefs.homeApps = emptyList()
                LauncherPrefs.edgeApps = emptyList()
                LauncherPrefs.seeded = false
                LauncherPrefs.commit()
                AppRepo.reload(ctx)
                Util.toast(ctx, "RESET")
            }
        }
    }
}

@Composable
fun GestureScreen() {
    val ctx = LocalContext.current
    val tick = resumeTick.intValue
    val svc = remember(tick) { isServiceEnabled(ctx) }
    ScreenColumn {
        HudPanel(Modifier.fillMaxWidth(), "ON THE HOME SCREEN") {
            SettingRow("SWIPE UP", "anywhere on home", LauncherPrefs.gUp.label) { LauncherPrefs.gUp = LauncherPrefs.gUp.next(); LauncherPrefs.commit() }
            SettingRow("SWIPE DOWN", "anywhere on home", LauncherPrefs.gDown.label) { LauncherPrefs.gDown = LauncherPrefs.gDown.next(); LauncherPrefs.commit() }
            SettingRow("DOUBLE TAP", "on empty space", LauncherPrefs.gDouble.label) { LauncherPrefs.gDouble = LauncherPrefs.gDouble.next(); LauncherPrefs.commit() }
            SettingRow("LONG PRESS", "on empty space", LauncherPrefs.gLong.label) { LauncherPrefs.gLong = LauncherPrefs.gLong.next(); LauncherPrefs.commit() }
            Hint("Tap a button to cycle through the actions. Swipe left/right changes the home page. Swipe down in the drawer (at the top) closes it. Back always returns to the main home page.")
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "OVER ALL APPS (NEEDS MUX SERVICE)") {
            StatusLine(svc, "MUX SERVICE IS RUNNING", "MUX SERVICE IS OFF (SETUP TAB)")
            SettingRow("EDGE BACK", "swipe in from the left / right screen edge = back", onOff(LauncherPrefs.edgeBack)) {
                LauncherPrefs.edgeBack = !LauncherPrefs.edgeBack; LauncherPrefs.commit()
            }
            SettingRow("BOTTOM BAR", "swipe up = home, up + hold = recent apps, sideways = back", onOff(LauncherPrefs.bottomBar)) {
                LauncherPrefs.bottomBar = !LauncherPrefs.bottomBar; LauncherPrefs.commit()
            }
            Hint("These draw thin touch strips above every app. Use them with 3-button navigation, or if you want extra gestures. With the phone's own gesture navigation they can trigger twice, so keep them OFF there.")
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "TEST") {
            Row(Modifier.fillMaxWidth().height(44.dp)) {
                HudButton("BACK", Modifier.weight(1f).height(44.dp), KeyboardKind(), fontSize = 10.sp) { Actions.run(ctx, GestureAction.BACK) }
                HudButton("HOME", Modifier.weight(1f).height(44.dp), KeyboardKind(), fontSize = 10.sp) { Actions.run(ctx, GestureAction.HOME) }
                HudButton("RECENTS", Modifier.weight(1f).height(44.dp), KeyboardKind(), fontSize = 10.sp) { Actions.run(ctx, GestureAction.RECENTS) }
            }
            Row(Modifier.fillMaxWidth().height(44.dp)) {
                HudButton("NOTIFY", Modifier.weight(1f).height(44.dp), KeyboardKind(), fontSize = 10.sp) { Actions.run(ctx, GestureAction.NOTIFICATIONS) }
                HudButton("QUICK", Modifier.weight(1f).height(44.dp), KeyboardKind(), fontSize = 10.sp) { Actions.run(ctx, GestureAction.QUICK) }
                HudButton("SHOT", Modifier.weight(1f).height(44.dp), KeyboardKind(), fontSize = 10.sp) { Actions.run(ctx, GestureAction.SHOT) }
            }
        }
    }
}

private fun KeyboardKind(): KeyKind = KeyKind.OP

@Composable
private fun AppListEditor(keys: List<String>, max: Int, onChange: (List<String>) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    for (a in AppRepo.resolve(keys)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(bitmap = a.icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            HudText(a.label, Modifier.weight(1f), size = 12.sp, maxLines = 1)
            HudButton("✕", Modifier.size(width = 42.dp, height = 34.dp), KeyKind.DANGER, fontSize = 12.sp) { onChange(keys - a.key) }
        }
    }
    if (keys.size < max) Btn("+ ADD APP", KeyKind.ACCENT) { adding = true }
    if (adding) {
        AppPickerDialog(
            title = "ADD APP",
            exclude = keys.toSet(),
            onPick = { onChange(keys + it.key); adding = false },
            onDismiss = { adding = false }
        )
    }
}

@Composable
fun DockScreen() {
    val ctx = LocalContext.current
    ScreenColumn {
        HudPanel(Modifier.fillMaxWidth(), "EDGE DOCK") {
            SettingRow("LEFT EDGE", "handle on the left", onOff(LauncherPrefs.edgeLeft)) {
                LauncherPrefs.edgeLeft = !LauncherPrefs.edgeLeft; LauncherPrefs.commit()
            }
            SettingRow("RIGHT EDGE", "handle on the right", onOff(LauncherPrefs.edgeRight)) {
                LauncherPrefs.edgeRight = !LauncherPrefs.edgeRight; LauncherPrefs.commit()
            }
            SettingRow("TAP MODE", "FULL screen, FLOAT window or SPLIT screen", LauncherPrefs.edgeMode) {
                LauncherPrefs.edgeMode = when (LauncherPrefs.edgeMode) { "FULL" -> "FLOAT"; "FLOAT" -> "SPLIT"; else -> "FULL" }
                LauncherPrefs.commit()
            }
            SettingRow("HANDLE HEIGHT", "where the handle sits on the edge", LauncherPrefs.handleY.toString() + "%") {
                LauncherPrefs.handleY = if (LauncherPrefs.handleY >= 80) 20 else LauncherPrefs.handleY + 10; LauncherPrefs.commit()
            }
            Btn("OPEN DOCK NOW", KeyKind.OP) { Actions.run(ctx, GestureAction.DOCK) }
            Hint("Swipe in from (or tap) the small handle on the screen edge, over any app. Needs MUX service (SETUP tab).")
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "WINDOW BAR") {
            SettingRow("WINDOW BAR", "small bar over apps: MIN / MAX / SPLIT / CLOSE", onOff(LauncherPrefs.windowBar)) {
                LauncherPrefs.windowBar = !LauncherPrefs.windowBar; LauncherPrefs.commit()
            }
            Hint("Tap the ◈ diamond to open the bar, drag it to move it. MIN = go home, MAX = leave split screen, SPLIT = split screen on/off, CLOSE = stop the app.")
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "EDGE DOCK APPS") {
            AppListEditor(LauncherPrefs.edgeApps, 12) { LauncherPrefs.edgeApps = it; LauncherPrefs.commit(false) }
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "HOME DOCK APPS (MAX 5)") {
            AppListEditor(LauncherPrefs.dock, 5) { LauncherPrefs.dock = it; LauncherPrefs.commit(false) }
        }
    }
}
