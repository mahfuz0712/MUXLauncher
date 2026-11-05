package com.mux.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * One thing on a home page. kind: 'a' app, 'f' folder, 'w' widget.
 * id: app key, folder id or widget id. keys: apps inside a folder. name: folder name or widget provider.
 */
data class Placed(
    val kind: Char,
    val id: String,
    val page: Int,
    val row: Int,
    val col: Int,
    val sw: Int = 1,
    val sh: Int = 1,
    val name: String = "",
    val keys: List<String> = emptyList()
)

/** The home screen pages (not counting the DASH page): free grid, any cell. Saved as JSON. */
object Grid {
    var items by mutableStateOf<List<Placed>>(emptyList())
        private set
    var pages by mutableIntStateOf(1)
        private set
    private var loaded = false

    val cols: Int get() = LauncherPrefs.homeCols.coerceIn(3, 6)
    val rows: Int get() = LauncherPrefs.homeRows.coerceIn(4, 8)

    fun init() {
        if (loaded) return
        loaded = true
        val raw = LauncherPrefs.getRaw("grid")
        if (raw != null) {
            try {
                val j = JSONObject(raw)
                pages = j.optInt("pages", 1).coerceAtLeast(1)
                val arr = j.getJSONArray("items")
                val out = ArrayList<Placed>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val ks = ArrayList<String>()
                    val ka = o.optJSONArray("ks")
                    if (ka != null) for (k in 0 until ka.length()) ks.add(ka.getString(k))
                    out.add(
                        Placed(
                            o.getString("t")[0], o.getString("i"), o.getInt("p"), o.getInt("r"), o.getInt("c"),
                            o.optInt("sw", 1), o.optInt("sh", 1), o.optString("n", ""), ks
                        )
                    )
                }
                items = out
                return
            } catch (e: Exception) {
            }
        }
        // first start of 1.0.1: take over the old flat home list
        if (LauncherPrefs.homeApps.isNotEmpty()) seedFrom(LauncherPrefs.homeApps)
    }

    private fun save() {
        val arr = JSONArray()
        for (p in items) {
            val o = JSONObject().put("t", p.kind.toString()).put("i", p.id).put("p", p.page).put("r", p.row).put("c", p.col)
            if (p.sw != 1) o.put("sw", p.sw)
            if (p.sh != 1) o.put("sh", p.sh)
            if (p.name.isNotEmpty()) o.put("n", p.name)
            if (p.keys.isNotEmpty()) o.put("ks", JSONArray(p.keys))
            arr.put(o)
        }
        LauncherPrefs.putRaw("grid", JSONObject().put("pages", pages).put("items", arr).toString())
    }

    private fun set(list: List<Placed>) {
        items = list
        save()
    }

    fun seedFrom(keys: List<String>) {
        if (items.isNotEmpty()) return
        val out = ArrayList<Placed>()
        var idx = 0
        val per = cols * rows
        for (k in keys) {
            out.add(Placed('a', k, idx / per, (idx % per) / cols, idx % cols))
            idx++
        }
        pages = maxOf(1, (idx + per - 1) / per)
        set(out)
    }

    private fun covers(p: Placed, page: Int, row: Int, col: Int): Boolean =
        p.page == page && row >= p.row && row < p.row + p.sh && col >= p.col && col < p.col + p.sw

    fun itemAt(page: Int, row: Int, col: Int, ignore: Placed? = null): Placed? =
        items.firstOrNull { it !== ignore && !(ignore != null && it.kind == ignore.kind && it.id == ignore.id) && covers(it, page, row, col) }

    fun areaFree(page: Int, row: Int, col: Int, sw: Int, sh: Int, ignore: Placed?): Boolean {
        if (row < 0 || col < 0 || row + sh > rows || col + sw > cols) return false
        for (r in row until row + sh) for (c in col until col + sw) if (itemAt(page, r, c, ignore) != null) return false
        return true
    }

    private fun findFree(sw: Int, sh: Int, startPage: Int): Triple<Int, Int, Int>? {
        for (pg in 0 until pages) {
            val page = (startPage + pg) % pages
            for (r in 0..rows - sh) for (c in 0..cols - sw) if (areaFree(page, r, c, sw, sh, null)) return Triple(page, r, c)
        }
        return null
    }

    fun find(kind: Char, id: String): Placed? = items.firstOrNull { it.kind == kind && it.id == id }

    fun hasApp(key: String): Boolean = items.any { (it.kind == 'a' && it.id == key) || (it.kind == 'f' && it.keys.contains(key)) }

    /** Put an app in the first free cell (current page first). Returns false when it is already on home. */
    fun addApp(key: String, nearPage: Int = 0): Boolean {
        if (hasApp(key)) return false
        var spot = findFree(1, 1, nearPage.coerceIn(0, pages - 1))
        if (spot == null) {
            pages += 1
            spot = Triple(pages - 1, 0, 0)
        }
        set(items + Placed('a', key, spot.first, spot.second, spot.third))
        return true
    }

    fun addAt(key: String, page: Int, row: Int, col: Int) {
        set(items + Placed('a', key, page, row, col))
    }

    fun addWidget(wid: Int, provider: String, sw0: Int, sh0: Int, nearPage: Int) {
        var sw = sw0.coerceIn(1, cols)
        var sh = sh0.coerceIn(1, rows)
        var spot = findFree(sw, sh, nearPage.coerceIn(0, pages - 1))
        while (spot == null && (sw > 1 || sh > 1)) {
            if (sh >= sw && sh > 1) sh-- else if (sw > 1) sw--
            spot = findFree(sw, sh, nearPage.coerceIn(0, pages - 1))
        }
        if (spot == null) {
            pages += 1
            spot = Triple(pages - 1, 0, 0)
        }
        set(items + Placed('w', wid.toString(), spot.first, spot.second, spot.third, sw, sh, provider))
    }

    fun remove(kind: Char, id: String) {
        set(items.filterNot { it.kind == kind && it.id == id })
    }

    fun removeAppEverywhere(key: String) {
        set(items.mapNotNull { fixFolder(if (it.kind == 'a' && it.id == key) null else it, key) })
    }

    private fun fixFolder(p: Placed?, removedKey: String): Placed? {
        p ?: return null
        if (p.kind != 'f' || !p.keys.contains(removedKey)) return p
        val ks = p.keys - removedKey
        return when {
            ks.isEmpty() -> null
            ks.size == 1 -> Placed('a', ks[0], p.page, p.row, p.col)
            else -> p.copy(keys = ks)
        }
    }

    fun move(p: Placed, page: Int, row: Int, col: Int) {
        set(items.map { if (it.kind == p.kind && it.id == p.id) it.copy(page = page, row = row, col = col) else it })
    }

    fun newFolderId(): String = "f" + System.currentTimeMillis().toString(36)

    /** Drop app [key] onto the app [target]: they become a folder. */
    fun makeFolder(target: Placed, key: String) {
        val f = Placed('f', newFolderId(), target.page, target.row, target.col, name = "FOLDER", keys = listOf(target.id, key))
        set(items.filterNot { it.kind == target.kind && it.id == target.id }.filterNot { it.kind == 'a' && it.id == key } + f)
    }

    fun addToFolder(folderId: String, key: String) {
        set(
            items.filterNot { it.kind == 'a' && it.id == key }.map {
                if (it.kind == 'f' && it.id == folderId && !it.keys.contains(key)) it.copy(keys = it.keys + key) else it
            }
        )
    }

    fun removeFromFolder(folderId: String, key: String) {
        set(items.mapNotNull { if (it.kind == 'f' && it.id == folderId) fixFolder(it, key) else it })
    }

    fun renameFolder(folderId: String, name: String) {
        set(items.map { if (it.kind == 'f' && it.id == folderId) it.copy(name = name.trim().ifEmpty { "FOLDER" }) else it })
    }

    fun clearAll() {
        pages = 1
        set(emptyList())
    }

    fun addPage() {
        pages += 1
        save()
    }

    /** Drop empty pages (always keeps one). */
    fun prune() {
        val used = items.map { it.page }.toSortedSet()
        if (used.isEmpty()) {
            if (pages != 1) { pages = 1; save() }
            return
        }
        val map = HashMap<Int, Int>()
        used.forEachIndexed { i, p -> map[p] = i }
        val changed = used.size != pages || used.any { map[it] != it }
        if (!changed) return
        pages = used.size
        set(items.map { it.copy(page = map[it.page] ?: 0) })
    }

    /** Forget apps that were uninstalled. */
    fun purgeMissing(installed: Set<String>) {
        if (installed.isEmpty()) return
        var out = items
        val gone = HashSet<String>()
        for (p in items) {
            if (p.kind == 'a' && p.id !in installed) gone.add(p.id)
            if (p.kind == 'f') for (k in p.keys) if (k !in installed) gone.add(k)
        }
        if (gone.isEmpty()) return
        for (k in gone) out = out.mapNotNull { fixFolder(if (it.kind == 'a' && it.id == k) null else it, k) }
        set(out)
    }
}
