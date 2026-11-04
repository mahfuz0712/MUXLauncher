package com.mux.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * All launcher settings. Shared by the launcher UI and the accessibility service (same process).
 * Change a field, then call [commit] to save it and tell the service to rebuild its overlays.
 */
object LauncherPrefs {
    private var sp: SharedPreferences? = null
    val listeners = mutableListOf<() -> Unit>()

    var amber by mutableStateOf(false)
    var wallpaper by mutableStateOf(false)
    var iconFrame by mutableStateOf(true)
    var showLabels by mutableStateOf(true)
    var homeCols by mutableStateOf(4)
    var drawerCols by mutableStateOf(4)
    var autoKeyboard by mutableStateOf(false)
    var haptic by mutableStateOf(true)

    var dock by mutableStateOf<List<String>>(emptyList())
    var homeApps by mutableStateOf<List<String>>(emptyList())
    var favorites by mutableStateOf<List<String>>(emptyList())
    var hidden by mutableStateOf<List<String>>(emptyList())
    var edgeApps by mutableStateOf<List<String>>(emptyList())
    var catMap by mutableStateOf<Map<String, String>>(emptyMap())

    var gUp by mutableStateOf(GestureAction.DRAWER)
    var gDown by mutableStateOf(GestureAction.NOTIFICATIONS)
    var gDouble by mutableStateOf(GestureAction.LOCK)
    var gLong by mutableStateOf(GestureAction.SETTINGS)

    var edgeLeft by mutableStateOf(true)
    var edgeRight by mutableStateOf(true)
    var edgeMode by mutableStateOf("FULL")
    var handleY by mutableStateOf(45)
    var windowBar by mutableStateOf(true)
    var edgeBack by mutableStateOf(false)
    var bottomBar by mutableStateOf(false)

    var seeded = false

    fun init(ctx: Context) {
        if (sp != null) return
        sp = ctx.applicationContext.getSharedPreferences("mux_launcher", Context.MODE_PRIVATE)
        load()
    }

    private fun list(k: String): List<String> =
        sp?.getString(k, null)?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()

    private fun load() {
        val p = sp ?: return
        amber = p.getBoolean("amber", false)
        wallpaper = p.getBoolean("wallpaper", false)
        iconFrame = p.getBoolean("iconFrame", true)
        showLabels = p.getBoolean("showLabels", true)
        homeCols = p.getInt("homeCols", 4)
        drawerCols = p.getInt("drawerCols", 4)
        autoKeyboard = p.getBoolean("autoKeyboard", false)
        haptic = p.getBoolean("haptic", true)
        dock = list("dock")
        homeApps = list("homeApps")
        favorites = list("favorites")
        hidden = list("hidden")
        edgeApps = list("edgeApps")
        catMap = list("catMap").mapNotNull {
            val i = it.indexOf('=')
            if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
        }.toMap()
        gUp = GestureAction.from(p.getString("gUp", null), GestureAction.DRAWER)
        gDown = GestureAction.from(p.getString("gDown", null), GestureAction.NOTIFICATIONS)
        gDouble = GestureAction.from(p.getString("gDouble", null), GestureAction.LOCK)
        gLong = GestureAction.from(p.getString("gLong", null), GestureAction.SETTINGS)
        edgeLeft = p.getBoolean("edgeLeft", true)
        edgeRight = p.getBoolean("edgeRight", true)
        edgeMode = p.getString("edgeMode", "FULL") ?: "FULL"
        handleY = p.getInt("handleY", 45)
        windowBar = p.getBoolean("windowBar", true)
        edgeBack = p.getBoolean("edgeBack", false)
        bottomBar = p.getBoolean("bottomBar", false)
        seeded = p.getBoolean("seeded", false)
    }

    fun commit(notify: Boolean = true) {
        val e = sp?.edit() ?: return
        e.putBoolean("amber", amber)
        e.putBoolean("wallpaper", wallpaper)
        e.putBoolean("iconFrame", iconFrame)
        e.putBoolean("showLabels", showLabels)
        e.putInt("homeCols", homeCols)
        e.putInt("drawerCols", drawerCols)
        e.putBoolean("autoKeyboard", autoKeyboard)
        e.putBoolean("haptic", haptic)
        e.putString("dock", dock.joinToString("|"))
        e.putString("homeApps", homeApps.joinToString("|"))
        e.putString("favorites", favorites.joinToString("|"))
        e.putString("hidden", hidden.joinToString("|"))
        e.putString("edgeApps", edgeApps.joinToString("|"))
        e.putString("catMap", catMap.entries.joinToString("|") { it.key + "=" + it.value })
        e.putString("gUp", gUp.name)
        e.putString("gDown", gDown.name)
        e.putString("gDouble", gDouble.name)
        e.putString("gLong", gLong.name)
        e.putBoolean("edgeLeft", edgeLeft)
        e.putBoolean("edgeRight", edgeRight)
        e.putString("edgeMode", edgeMode)
        e.putInt("handleY", handleY)
        e.putBoolean("windowBar", windowBar)
        e.putBoolean("edgeBack", edgeBack)
        e.putBoolean("bottomBar", bottomBar)
        e.putBoolean("seeded", seeded)
        e.apply()
        if (notify) for (l in listeners.toList()) l()
    }

    /** Add [key] to a list if missing, remove it if present. */
    fun toggled(list: List<String>, key: String, max: Int = 99): List<String> =
        if (list.contains(key)) list - key else if (list.size >= max) list else list + key
}
