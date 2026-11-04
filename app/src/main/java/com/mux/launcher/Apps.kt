package com.mux.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** ALL and FAV are virtual filters. The rest can be assigned to an app. */
enum class Cat(val label: String) {
    ALL("ALL"), FAV("FAV"), SOCIAL("SOCIAL"), MEDIA("MEDIA"), GAMES("GAMES"), WORK("WORK"),
    TOOLS("TOOLS"), MONEY("MONEY"), TRAVEL("TRAVEL"), SHOP("SHOP"), OTHER("OTHER");

    val assignable: Boolean get() = this != ALL && this != FAV

    companion object {
        fun from(name: String?): Cat? = values().firstOrNull { it.name == name }
    }
}

class AppEntry(
    val pkg: String,
    val cls: String,
    val label: String,
    val system: Boolean,
    val auto: Cat,
    val bitmap: Bitmap
) {
    val key: String get() = "$pkg/$cls"
    val icon: ImageBitmap = bitmap.asImageBitmap()

    /** The category after the user's own override (if any). */
    val cat: Cat get() = Cat.from(LauncherPrefs.catMap[key]) ?: auto
}

object AppRepo {
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    private var loading = false

    private val rules: List<Pair<Cat, List<String>>> = listOf(
        Cat.GAMES to listOf("game", "pubg", "freefire", "ludo", "minecraft", "roblox", "supercell", "candy", "carrom", "garena", "miniclip"),
        Cat.MONEY to listOf("bkash", "nagad", "rocket", "bank", "wallet", "finance", "upay", "paypal", "binance", "payment"),
        Cat.SOCIAL to listOf("facebook", "whatsapp", "telegram", "instagram", "twitter", "snapchat", "messenger", "viber", "discord", "linkedin", "tiktok", "reddit", "pinterest", "signal", "skype", "zoom", "mms", "messaging", "contacts", "dialer", "phone", "email", "gmail", "mail"),
        Cat.MEDIA to listOf("youtube", "spotify", "music", "video", "player", "gallery", "photos", "camera", "vlc", "netflix", "soundcloud", "radio", "podcast", "recorder"),
        Cat.TRAVEL to listOf("maps", "uber", "pathao", "travel", "navigation", "taxi", "flight", "booking", "weather", "transit"),
        Cat.SHOP to listOf("daraz", "shop", "amazon", "ebay", "aliexpress", "foodpanda", "market", "cart"),
        Cat.WORK to listOf("office", "docs", "sheets", "slides", "pdf", "note", "calendar", "drive", "word", "excel", "powerpoint", "reader", "scanner", "dropbox", "notion", "todo"),
        Cat.TOOLS to listOf("settings", "calculator", "files", "filemanager", "documentsui", "clock", "flashlight", "torch", "compass", "cleaner", "security", "backup", "clone", "vpn", "browser", "chrome", "firefox", "update", "launcher", "keyboard", "vending", "inputmethod")
    )

    @Suppress("DEPRECATION")
    private fun classify(info: ApplicationInfo, pkg: String): Cat {
        if (info.flags and ApplicationInfo.FLAG_IS_GAME != 0) return Cat.GAMES
        // 0 GAME, 1 AUDIO, 2 VIDEO, 3 IMAGE, 4 SOCIAL, 5 NEWS, 6 MAPS, 7 PRODUCTIVITY
        when (info.category) {
            ApplicationInfo.CATEGORY_GAME -> return Cat.GAMES
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> return Cat.MEDIA
            ApplicationInfo.CATEGORY_SOCIAL -> return Cat.SOCIAL
            ApplicationInfo.CATEGORY_MAPS -> return Cat.TRAVEL
            ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_NEWS -> return Cat.WORK
        }
        val p = pkg.lowercase()
        for ((cat, words) in rules) {
            if (words.any { p.contains(it) }) return cat
        }
        return if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) Cat.TOOLS else Cat.OTHER
    }

    /** Reads every launchable app. Safe to call from any thread. */
    fun loadSync(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val out = ArrayList<AppEntry>()
        val seen = HashSet<String>()
        for (ri in pm.queryIntentActivities(q, 0)) {
            val ai = ri.activityInfo ?: continue
            val key = ai.packageName + "/" + ai.name
            if (!seen.add(key)) continue
            try {
                val label = ri.loadLabel(pm).toString()
                val bmp = ri.loadIcon(pm).toBitmap(112, 112)
                val sys = ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                out.add(AppEntry(ai.packageName, ai.name, label, sys, classify(ai.applicationInfo, ai.packageName), bmp))
            } catch (e: Exception) {
            }
        }
        out.sortBy { it.label.lowercase() }
        return out
    }

    fun reload(ctx: Context) {
        if (loading) return
        loading = true
        val app = ctx.applicationContext
        Thread {
            val list = try { loadSync(app) } catch (e: Exception) { emptyList() }
            Handler(Looper.getMainLooper()).post {
                apps = list
                loading = false
                seedDefaults(app)
            }
        }.start()
    }

    fun ensure(ctx: Context) {
        if (apps.isEmpty()) reload(ctx)
    }

    fun byKey(key: String): AppEntry? = apps.firstOrNull { it.key == key }

    fun resolve(keys: List<String>): List<AppEntry> = keys.mapNotNull { byKey(it) }

    fun firstOfPkg(pkg: String): AppEntry? = apps.firstOrNull { it.pkg == pkg }

    private fun pkgOf(pm: PackageManager, i: Intent): String? = try {
        pm.resolveActivity(i, 0)?.activityInfo?.packageName?.takeIf { it != "android" }
    } catch (e: Exception) {
        null
    }

    private fun mainCat(cat: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, cat)

    /** First run: fill dock / edge dock / home with the phone's own default apps. */
    private fun seedDefaults(ctx: Context) {
        if (LauncherPrefs.seeded || apps.isEmpty()) return
        val pm = ctx.packageManager
        fun keys(vararg intents: Intent): List<String> =
            intents.mapNotNull { pkgOf(pm, it) }.distinct().mapNotNull { firstOfPkg(it)?.key }

        LauncherPrefs.dock = keys(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:")),
            mainCat(Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent(Settings.ACTION_SETTINGS)
        ).take(5)
        LauncherPrefs.edgeApps = keys(
            mainCat(Intent.CATEGORY_APP_EMAIL),
            mainCat(Intent.CATEGORY_APP_GALLERY),
            mainCat(Intent.CATEGORY_APP_CALCULATOR),
            mainCat(Intent.CATEGORY_APP_CALENDAR),
            mainCat(Intent.CATEGORY_APP_MUSIC),
            mainCat(Intent.CATEGORY_APP_MAPS)
        ).take(7)
        LauncherPrefs.homeApps = keys(
            mainCat(Intent.CATEGORY_APP_GALLERY),
            mainCat(Intent.CATEGORY_APP_CALENDAR),
            mainCat(Intent.CATEGORY_APP_CALCULATOR),
            mainCat(Intent.CATEGORY_APP_MAPS),
            mainCat(Intent.CATEGORY_APP_MUSIC),
            mainCat(Intent.CATEGORY_APP_EMAIL),
            Intent(Settings.ACTION_SETTINGS)
        ).filter { !LauncherPrefs.dock.contains(it) }
        LauncherPrefs.seeded = true
        LauncherPrefs.commit()
    }

    fun intentFor(e: AppEntry): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(e.pkg, e.cls))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}
