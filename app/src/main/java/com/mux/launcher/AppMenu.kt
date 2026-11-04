package com.mux.launcher

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun MenuBtn(label: String, kind: KeyKind = KeyKind.OP, onClick: () -> Unit) {
    HudButton(label, Modifier.fillMaxWidth().height(40.dp), kind, fontSize = 12.sp, onClick = onClick)
}

/** Long-press menu of an app. */
@Composable
fun AppMenuDialog(app: AppEntry, onDismiss: () -> Unit, onSplit: (AppEntry) -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf("MENU") }
    val k = app.key

    fun change(block: () -> Unit) {
        block()
        LauncherPrefs.commit()
        onDismiss()
    }

    HudDialog(app.label.uppercase(), onDismiss) {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            if (mode == "MENU") {
                MenuBtn("OPEN", KeyKind.ACCENT) { Launch.open(ctx, app); onDismiss() }
                MenuBtn("OPEN IN FLOATING WINDOW") { Launch.floating(ctx, app); onDismiss() }
                MenuBtn("SPLIT SCREEN WITH ...") { onSplit(app) }
                MenuBtn(if (LauncherPrefs.homeApps.contains(k)) "REMOVE FROM HOME" else "ADD TO HOME") {
                    change { LauncherPrefs.homeApps = LauncherPrefs.toggled(LauncherPrefs.homeApps, k) }
                }
                MenuBtn(if (LauncherPrefs.dock.contains(k)) "REMOVE FROM DOCK" else "ADD TO DOCK") {
                    if (!LauncherPrefs.dock.contains(k) && LauncherPrefs.dock.size >= 5) {
                        Util.toast(ctx, "DOCK IS FULL (5)")
                    } else {
                        change { LauncherPrefs.dock = LauncherPrefs.toggled(LauncherPrefs.dock, k, 5) }
                    }
                }
                MenuBtn(if (LauncherPrefs.edgeApps.contains(k)) "REMOVE FROM EDGE DOCK" else "ADD TO EDGE DOCK") {
                    change { LauncherPrefs.edgeApps = LauncherPrefs.toggled(LauncherPrefs.edgeApps, k, 12) }
                }
                MenuBtn(if (LauncherPrefs.favorites.contains(k)) "REMOVE FAVORITE" else "ADD TO FAVORITES") {
                    change { LauncherPrefs.favorites = LauncherPrefs.toggled(LauncherPrefs.favorites, k) }
                }
                MenuBtn("CATEGORY: " + app.cat.label) { mode = "CAT" }
                MenuBtn("HIDE APP") { change { LauncherPrefs.hidden = LauncherPrefs.hidden + k } }
                MenuBtn("APP INFO", KeyKind.FN) {
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                    }
                    onDismiss()
                }
                if (!app.system) {
                    MenuBtn("UNINSTALL", KeyKind.DANGER) {
                        try {
                            ctx.startActivity(
                                Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                        }
                        onDismiss()
                    }
                }
            } else {
                HudText("CHOOSE CATEGORY", color = Hud.colors.dim, size = 11.sp)
                Spacer(Modifier.height(4.dp))
                for (c in Cat.values()) {
                    if (!c.assignable) continue
                    MenuBtn(c.label + (if (c == app.cat) "  ✓" else "")) {
                        change { LauncherPrefs.catMap = LauncherPrefs.catMap + (k to c.name) }
                    }
                }
                MenuBtn("AUTOMATIC (" + app.auto.label + ")", KeyKind.FN) {
                    change { LauncherPrefs.catMap = LauncherPrefs.catMap - k }
                }
            }
        }
    }
}

/** Pick one app from a searchable list. */
@Composable
fun AppPickerDialog(title: String, exclude: Set<String> = emptySet(), onPick: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = AppRepo.apps.filter { !exclude.contains(it.key) && (q.isBlank() || it.label.contains(q.trim(), true)) }
    HudDialog(title, onDismiss) {
        HudField(q, "SEARCH", Modifier.fillMaxWidth().height(42.dp)) { q = it }
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            items(list, key = { it.key }) { a ->
                HudItem(Modifier.fillMaxWidth(), onClick = { onPick(a) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(bitmap = a.icon, contentDescription = null, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        HudText(a.label, size = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
