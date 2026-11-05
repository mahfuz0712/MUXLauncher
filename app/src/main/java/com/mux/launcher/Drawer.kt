package com.mux.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Full-screen app drawer: search bar, category chips, A-Z rail, long-press menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerScreen(
    focusSearch: Boolean,
    onClose: () -> Unit,
    onOpen: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit
) {
    val c = Hud.colors
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var catName by rememberSaveable { mutableStateOf(Cat.ALL.name) }
    val cat = Cat.from(catName) ?: Cat.ALL
    val focus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val closeNow by rememberUpdatedState(onClose)

    val hidden = LauncherPrefs.hidden
    val favs = LauncherPrefs.favorites
    val catOverrides = LauncherPrefs.catMap

    // Cache the visible list and category counts instead of rebuilding them on every recomposition.
    val visible = remember(AppRepo.apps, hidden) {
        AppRepo.apps.filterNot { it.key in hidden }
    }
    fun inCat(a: AppEntry, k: Cat): Boolean = when (k) {
        Cat.ALL -> true
        Cat.FAV -> a.key in favs
        else -> (Cat.from(catOverrides[a.key]) ?: a.auto) == k
    }
    val counts = remember(visible, favs, catOverrides) {
        buildMap {
            put(Cat.ALL, visible.size)
            put(Cat.FAV, visible.count { it.key in favs })
            Cat.values().filter { it != Cat.ALL && it != Cat.FAV }.forEach { category ->
                put(category, visible.count { (Cat.from(catOverrides[it.key]) ?: it.auto) == category })
            }
        }
    }
    val categories = remember(counts) {
        Cat.values().filter { it == Cat.ALL || (counts[it] ?: 0) > 0 }
    }
    val q = query.trim()
    val list = remember(visible, cat, q, favs, catOverrides) {
        visible.filter {
            inCat(it, cat) && (q.isEmpty() || it.label.contains(q, true) || it.pkg.contains(q, true))
        }
    }
    val density = LocalDensity.current
    val horizontalThreshold = with(density) { 64.dp.toPx() }
    val closeThreshold = with(density) { 90.dp.toPx() }

    LaunchedEffect(categories, catName) {
        if (cat !in categories) catName = Cat.ALL.name
    }

    LaunchedEffect(focusSearch) {
        if (focusSearch || LauncherPrefs.autoKeyboard) {
            delay(280)
            try { focus.requestFocus() } catch (e: Exception) { }
        }
    }

    BackHandler(true) {
        if (query.isNotEmpty()) query = "" else if (cat != Cat.ALL) catName = Cat.ALL.name else closeNow()
    }

    // Pull down at the top of the list to close.
    val closeThresholdPx by rememberUpdatedState(closeThreshold)
    val nested = remember {
        object : NestedScrollConnection {
            var acc = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag) acc += available.y
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (acc > closeThresholdPx) closeNow()
                acc = 0f
                return Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg.copy(alpha = if (LauncherPrefs.wallpaper) 0.88f else 0.97f))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                HudField(
                    value = query,
                    hint = "⌕  SEARCH ${visible.size} APPS",
                    modifier = Modifier.weight(1f).height(44.dp),
                    focus = focus,
                    onDone = { list.firstOrNull()?.let { onOpen(it) } }
                ) { query = it }
                Spacer(Modifier.width(6.dp))
                HudButton("▾", Modifier.width(46.dp).height(44.dp), KeyKind.ACCENT, fontSize = 18.sp) { onClose() }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                for (k in categories) {
                    val n = counts[k] ?: 0
                    HudButton(
                        label = k.label + " " + n,
                        modifier = Modifier.height(34.dp).widthIn(min = 70.dp),
                        kind = if (k == Cat.FAV) KeyKind.ACCENT else KeyKind.OP,
                        selected = k == cat,
                        fontSize = 10.sp
                    ) { catName = k.name }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (list.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        EmptyNote(if (AppRepo.apps.isEmpty()) "LOADING APPS..." else "NO APPS FOUND")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(LauncherPrefs.drawerCols.coerceIn(3, 6)),
                        state = gridState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .nestedScroll(nested)
                            .pointerInput(cat, categories, horizontalThreshold) {
                                var dragX = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { dragX = 0f },
                                    onHorizontalDrag = { change, amount ->
                                        dragX += amount
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        if (kotlin.math.abs(dragX) >= horizontalThreshold && categories.size > 1) {
                                            val index = categories.indexOf(cat).coerceAtLeast(0)
                                            val next = if (dragX < 0f) {
                                                (index + 1).coerceAtMost(categories.lastIndex)
                                            } else {
                                                (index - 1).coerceAtLeast(0)
                                            }
                                            if (next != index) {
                                                catName = categories[next].name
                                                scope.launch { gridState.scrollToItem(0) }
                                            }
                                        }
                                    },
                                    onDragCancel = { dragX = 0f }
                                )
                            }
                    ) {
                        items(list, key = { it.key }, contentType = { "app" }) { a ->
                            AppTile(a, Modifier.fillMaxWidth(), src = Src.DRAWER, onClick = { onOpen(a) }, onLong = { onMenu(a) })
                        }
                    }
                }
                if (q.isEmpty() && list.size > 24) {
                    val letters = LinkedHashMap<Char, Int>()
                    list.forEachIndexed { i, a ->
                        val ch = a.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
                        if (!letters.containsKey(ch)) letters[ch] = i
                    }
                    Column(Modifier.width(18.dp).fillMaxHeight().padding(vertical = 2.dp)) {
                        for ((ch, idx) in letters) {
                            Box(
                                Modifier.weight(1f).fillMaxWidth().clickable { scope.launch { gridState.scrollToItem(idx) } },
                                contentAlignment = Alignment.Center
                            ) {
                                HudText(ch.toString(), color = c.dim, size = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
