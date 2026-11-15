package dev.kreal.muzon.ui

import androidx.compose.foundation.gestures.animateScrollBy

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.kreal.muzon.R
import dev.kreal.muzon.data.Album
import dev.kreal.muzon.data.AppTheme
import dev.kreal.muzon.data.Library
import dev.kreal.muzon.data.LibrarySource
import dev.kreal.muzon.data.Place
import dev.kreal.muzon.data.Playlist
import dev.kreal.muzon.data.PlaylistSort
import dev.kreal.muzon.data.Sorting
import dev.kreal.muzon.data.TileSize
import dev.kreal.muzon.data.Track
import dev.kreal.muzon.data.Search
import sh.calvin.reorderable.ReorderableItem

/** Space the shell keeps at the bottom of every list: tabs and mini player, or just the system bar. */
val LocalBottomSpace = compositionLocalOf { 0.dp }

@Composable
private fun statusTop() = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

@Composable
fun RouteContent(route: Route, vm: AppViewModel, library: Library) {
    when (route) {
        is Route.Root -> when (route.tab) {
            Tab.Home -> HomeScreen(vm, library)
            Tab.Playlists -> PlaylistsScreen(vm, library)
            Tab.Artists -> ArtistsScreen(vm, library)
            Tab.Albums -> AlbumsScreen(vm, library)
            Tab.Songs -> SongsScreen(vm, library, route, stringResource(R.string.place_songs), library.songs, true)
        }
        // A playlist deleted here or on the computer closes its page rather than leave it blank.
        is Route.PlaylistRoute -> library.playlist(route.id)?.let { PlaylistScreen(vm, library, route, it) } ?: LeaveMissing(vm, route)
        is Route.AddSongsRoute -> library.playlist(route.playlistId)?.let { AddSongsScreen(vm, library, it) } ?: LeaveMissing(vm, route)
        is Route.ArtistRoute -> library.artistsByKey[route.artistKey]?.let { ArtistScreen(vm, library, route, it) }
        is Route.AlbumRoute -> library.albumsByKey[route.albumKey]?.let { AlbumScreen(vm, route, it) }
        is Route.GenreRoute -> library.genres.firstOrNull { it.first == route.name }?.let { SongsScreen(vm, library, route, it.first, it.second, false) }
        Route.RecentRoute -> SongsScreen(vm, library, route, stringResource(R.string.place_recent), library.recentlyAdded(vm.prefs.recentDays), false)
        Route.GenresRoute -> GenresScreen(vm, library)
        Route.HistoryRoute -> SongsScreen(vm, library, route, stringResource(R.string.place_history), vm.historyTracks(library), false)
        Route.MostPlayedRoute -> SongsScreen(vm, library, route, stringResource(R.string.place_most), vm.mostPlayed(library), false)
        is Route.FolderRoute -> FolderScreen(vm, library, route)
        Route.SearchRoute -> SearchScreen(vm, library)
        Route.SettingsRoute -> SettingsScreen(vm, library)
    }
}

@Composable
private fun LeaveMissing(vm: AppViewModel, route: Route) {
    LaunchedEffect(route) { if (vm.nav.stack.lastOrNull() == route) vm.nav.pop() }
}

private fun Place.glyph() = when (this) {
    Place.Playlists -> GlyphKind.Playlists
    Place.Artists -> GlyphKind.Artists
    Place.Albums -> GlyphKind.Albums
    Place.Songs -> GlyphKind.Songs
    Place.Recent -> GlyphKind.Recent
    Place.Genres -> GlyphKind.Genres
    Place.History -> GlyphKind.History
    Place.MostPlayed -> GlyphKind.MostPlayed
    Place.Folders -> GlyphKind.Folders
}

@Composable
fun Place.label() = stringResource(
    when (this) {
        Place.Playlists -> R.string.place_playlists
        Place.Artists -> R.string.place_artists
        Place.Albums -> R.string.place_albums
        Place.Songs -> R.string.place_songs
        Place.Recent -> R.string.place_recent
        Place.Genres -> R.string.place_genres
        Place.History -> R.string.place_history
        Place.MostPlayed -> R.string.place_most
        Place.Folders -> R.string.place_folders
    },
)

fun AppViewModel.historyTracks(library: Library): List<Track> {
    prefs.playsVersion
    return prefs.history.mapNotNull(library.byId::get)
}

/** By plays on the Mac plus plays on this phone. */
fun AppViewModel.mostPlayed(library: Library): List<Track> {
    prefs.playsVersion
    return library.tracks.map { it to it.playCount + prefs.localPlays(it.id) }.filter { it.second > 0 }
        .sortedByDescending { it.second }.take(100).map { it.first }
}

private fun Library.count(place: Place, vm: AppViewModel) = when (place) {
    Place.History -> vm.historyTracks(this).size
    Place.MostPlayed -> vm.mostPlayed(this).size
    Place.Folders -> subfolders("").size
    Place.Playlists -> playlists.size
    Place.Artists -> artists.size
    Place.Albums -> albums.size
    Place.Songs -> tracks.size
    Place.Recent -> recentlyAdded(vm.prefs.recentDays).size
    Place.Genres -> genres.size
}

private fun AppViewModel.openPlace(place: Place) {
    when (place) {
        Place.Playlists -> nav.select(Tab.Playlists)
        Place.Artists -> nav.select(Tab.Artists)
        Place.Albums -> nav.select(Tab.Albums)
        Place.Songs -> nav.select(Tab.Songs)
        Place.Recent -> nav.push(Route.RecentRoute)
        Place.Genres -> nav.push(Route.GenresRoute)
        Place.History -> nav.push(Route.HistoryRoute)
        Place.MostPlayed -> nav.push(Route.MostPlayedRoute)
        Place.Folders -> nav.push(Route.FolderRoute(""))
    }
}

// ---- Home

/**
 * One smooth glide to the top, as in Music on the iPhone: the distance is estimated from the rows on
 * screen and run as a single animation, then snapped exactly. The lazy list's own animation to an
 * item far away jumps part of the way first and then slides, which reads as a jerk.
 */
suspend fun androidx.compose.foundation.lazy.LazyListState.glideToTop() {
    val info = layoutInfo.visibleItemsInfo
    if (info.isEmpty()) return
    val row = info.sumOf { it.size } / info.size
    val distance = firstVisibleItemIndex * row + firstVisibleItemScrollOffset
    animateScrollBy(-distance.toFloat() - row, androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    scrollToItem(0)
}

suspend fun androidx.compose.foundation.lazy.grid.LazyGridState.glideToTop() {
    val info = layoutInfo.visibleItemsInfo
    if (info.isEmpty()) return
    val perRow = info.count { it.row == info.first().row }.coerceAtLeast(1)
    val row = info.maxOf { it.size.height } + 10
    val distance = firstVisibleItemIndex / perRow * row + firstVisibleItemScrollOffset
    animateScrollBy(-distance.toFloat() - row, androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    scrollToItem(0)
}

/** Scrolls a tab's first page to the top when its tab is tapped again. */
@Composable
fun ScrollToTopOnTap(vm: AppViewModel, tab: Tab, scroll: suspend () -> Unit) {
    val taps = vm.nav.topTaps[tab] ?: 0
    LaunchedEffect(taps) { if (taps > 0) scroll() }
}

@Composable
private fun HomeScreen(vm: AppViewModel, library: Library) {
    val p = LocalPalette.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val found = remember(q, library) { searchAll(library, q) }
    SelectionPool(vm, Route.Root(Tab.Home), found.songs)
    // Back empties the search and puts the keyboard away with it.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.activity.compose.BackHandler(enabled = query.isNotEmpty()) {
        query = ""
        focusManager.clearFocus(force = true)
    }
    val theme = LocalAppTheme.current
    val places = vm.prefs.places
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    ScrollToTopOnTap(vm, Tab.Home) { grid.glideToTop() }
    LazyVerticalGrid(
        state = grid,
        columns = GridCells.Adaptive(100.dp),
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !moving, // a tile under the finger must not drag the page with it
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = statusTop() + 6.dp, bottom = LocalBottomSpace.current + 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "search", span = { GridItemSpan(maxLineSpan) }, contentType = "search") {
            SearchField(query, { query = it })
        }
        if (q.isNotEmpty()) {
            searchResults(vm, library, found)
            return@LazyVerticalGrid
        }
        item(key = "places", span = { GridItemSpan(maxLineSpan) }, contentType = "places") {
            PlaceTiles(vm, library, places, theme, editing, { moving = it }) { editing = true }
        }
        if (library.source != LibrarySource.MusicDatabase && vm.prefs.treeUri != null) {
            item(key = "noDb", span = { GridItemSpan(maxLineSpan) }, contentType = "noDb") { NoLibraryCard(vm) }
        }
        item(key = "actions", span = { GridItemSpan(maxLineSpan) }, contentType = "actions") {
            Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                Tappable({ editing = !editing }, Modifier.clip(RoundedCornerShape(8.dp)).padding(4.dp)) {
                    Txt(stringResource(if (editing) R.string.done else R.string.edit), Type.small, if (editing) p.accent else p.muted)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.nav.push(Route.SettingsRoute) }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(GlyphKind.Settings, 18.dp, p.muted)
                    Spacer(Modifier.width(5.dp))
                    Txt(stringResource(R.string.settings), Type.small, p.muted)
                }
            }
        }
        items(library.playlists, key = { "p:${it.id}" }, contentType = { "cover" }) { playlist ->
            CoverCell(playlist.name, library.tracksOf(playlist).size.toString(), { Mosaic(library.tracksOf(playlist), Modifier.fillMaxSize()) }, vm, library.tracksOf(playlist), playlist) {
                vm.nav.push(Route.PlaylistRoute(playlist.id))
            }
        }
        items(library.albums, key = { "a:${it.key}" }, contentType = { "cover" }) { album ->
            CoverCell(album.title, album.artist, { TrackArt(album.tracks.first(), Modifier.fillMaxSize(), Radius8) }, vm, album.tracks) {
                vm.nav.push(Route.AlbumRoute(album.key))
            }
        }
    }
}

/**
 * Half-width tiles pair up; a wider one takes its own row. Every tile is placed by its own animated
 * offset, so a reorder or a resize slides tiles into place instead of jumping. In edit mode the grid
 * itself follows the finger: it never loses a drag when tiles change rows. A long press opens edit
 * mode; the corner handle resizes, the badge hides, hidden places wait below to be added back.
 */
@Composable
private fun PlaceTiles(vm: AppViewModel, library: Library, places: List<Place>, theme: AppTheme, editing: Boolean, onMoving: (Boolean) -> Unit, onEdit: () -> Unit) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val full = maxWidth
        val half = (maxWidth - gap) / 2
        // Slots in dp, computed in order: two halves share a row, anything wider gets its own.
        val slots = HashMap<Place, androidx.compose.ui.unit.DpRect>()
        var x = 0.dp
        var y = 0.dp
        var rowHeight = 0.dp
        for (place in places) {
            val size = vm.prefs.tileSize(place)
            val w = if (size == TileSize.Half) half else full
            val h = if (size == TileSize.Large) 120.dp else 82.dp
            if (x > 0.dp && (size != TileSize.Half || x + w > full + 1.dp)) {
                x = 0.dp
                y += rowHeight + gap
                rowHeight = 0.dp
            }
            slots[place] = androidx.compose.ui.unit.DpRect(x, y, x + w, y + h)
            rowHeight = maxOf(rowHeight, h)
            if (size == TileSize.Half) x += w + gap else { x = 0.dp; y += rowHeight + gap; rowHeight = 0.dp }
        }
        val height = if (x > 0.dp) y + rowHeight else (y - gap).coerceAtLeast(0.dp)

        var dragging by remember { mutableStateOf<Place?>(null) }
        val dragTopLeft = remember { mutableStateOf(Offset.Zero) }
        val currentSlots by androidx.compose.runtime.rememberUpdatedState(slots)
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .then(
                        if (!editing) Modifier else Modifier.pointerInput(Unit) {
                            fun rectPx(r: androidx.compose.ui.unit.DpRect) = androidx.compose.ui.geometry.Rect(r.left.toPx(), r.top.toPx(), r.right.toPx(), r.bottom.toPx())
                            detectDragGestures(
                                onDragStart = { start ->
                                    val hit = currentSlots.entries.firstOrNull { rectPx(it.value).contains(start) }
                                    if (hit != null) {
                                        dragging = hit.key
                                        dragTopLeft.value = rectPx(hit.value).topLeft
                                        onMoving(true)
                                    }
                                },
                                onDragEnd = { dragging = null; onMoving(false) },
                                onDragCancel = { dragging = null; onMoving(false) },
                                onDrag = { change, amount ->
                                    val place = dragging ?: return@detectDragGestures
                                    change.consume()
                                    dragTopLeft.value += amount
                                    val own = rectPx(currentSlots[place] ?: return@detectDragGestures)
                                    val center = dragTopLeft.value + Offset(own.width / 2, own.height / 2)
                                    val over = currentSlots.entries.firstOrNull { it.key != place && rectPx(it.value).contains(center) }?.key
                                    if (over != null) {
                                        val list = vm.prefs.places.toMutableList()
                                        val from = list.indexOf(place)
                                        val to = list.indexOf(over)
                                        if (from >= 0 && to >= 0) {
                                            list.add(to, list.removeAt(from))
                                            vm.prefs.updatePlaces(list)
                                        }
                                    }
                                },
                            )
                        },
                    ),
            ) {
                places.forEach { place ->
                    val slot = slots.getValue(place)
                    androidx.compose.runtime.key(place) {
                        val spec = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.85f, stiffness = 420f)
                        val target = with(density) { androidx.compose.ui.unit.IntOffset(slot.left.roundToPx(), slot.top.roundToPx()) }
                        val moved by androidx.compose.animation.core.animateIntOffsetAsState(target, spec, label = "slot")
                        val w by androidx.compose.animation.core.animateDpAsState(slot.right - slot.left, spring(stiffness = 420f), label = "w")
                        val h by androidx.compose.animation.core.animateDpAsState(slot.bottom - slot.top, spring(stiffness = 420f), label = "h")
                        PlaceTile(
                            vm, library, place, theme, editing,
                            Modifier
                                .size(w, h)
                                .zIndex(if (dragging == place) 1f else 0f)
                                .offset {
                                    if (dragging == place) {
                                        androidx.compose.ui.unit.IntOffset(dragTopLeft.value.x.toInt(), dragTopLeft.value.y.toInt())
                                    } else {
                                        moved
                                    }
                                }
                                .graphicsLayer {
                                    val lifted = dragging == place
                                    scaleX = if (lifted) 1.04f else 1f
                                    scaleY = if (lifted) 1.04f else 1f
                                },
                            onMoving,
                            onEdit,
                        )
                    }
                }
            }
            if (editing) {
                val hidden = Place.entries.filter { it !in places }
                if (hidden.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        hidden.forEach { place ->
                            Row(
                                Modifier.clip(RoundedCornerShape(16.dp)).background(p.surfaceHigh)
                                    .clickable { vm.prefs.updatePlaces(vm.prefs.places + place) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Add, null, tint = Blocks.forPlace(place), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Txt(place.label(), Type.small, p.text)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceTile(vm: AppViewModel, library: Library, place: Place, theme: AppTheme, editing: Boolean, modifier: Modifier, onMoving: (Boolean) -> Unit, onEdit: () -> Unit) {
    val color = Blocks.forPlace(place)
    val on = Blocks.on(color)
    val size = vm.prefs.tileSize(place)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(modifier) {
        // Name on top across the whole tile, count under it, covers in the lower right corner.
        Box(
            Modifier
                .fillMaxSize()
                .block(color, theme, 16.dp)
                .then(
                    if (editing) Modifier else Modifier.combinedClickable(
                        onClick = { vm.openPlace(place) },
                        onLongClick = {
                            if (vm.prefs.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onEdit()
                        },
                    ),
                )
                .padding(12.dp),
        ) {
            Column(Modifier.align(Alignment.TopStart)) {
                Txt(place.label(), if (size == TileSize.Half) Type.tile else Type.section, on)
                Txt(library.count(place, vm).toString(), Type.small, on)
            }
            Box(Modifier.align(Alignment.BottomEnd)) { PlaceThumbs(vm, library, place, size) }
        }
        if (editing) {
            // Hide.
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { if (vm.prefs.places.size > 1) vm.prefs.updatePlaces(vm.prefs.places - place) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Remove, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            // Resize: drag sideways for width, down for height; a tap steps through the sizes.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { vm.prefs.cycleTileSize(place) }
                    .pointerInput(place) {
                        var dx = 0f
                        var dy = 0f
                        detectDragGestures(
                            onDragStart = { dx = 0f; dy = 0f; onMoving(true) },
                            onDragEnd = { onMoving(false) },
                            onDragCancel = { onMoving(false) },
                        ) { change, amount ->
                            change.consume()
                            dx += amount.x
                            dy += amount.y
                            val step = 36.dp.toPx()
                            val current = vm.prefs.tileSize(place)
                            val next = when {
                                dy > step -> TileSize.Large
                                dy < -step && current == TileSize.Large -> TileSize.Full
                                dx > step && current == TileSize.Half -> TileSize.Full
                                dx < -step -> TileSize.Half
                                else -> null
                            }
                            if (next != null && next != current) {
                                vm.prefs.setTileSize(place, next)
                                dx = 0f
                                dy = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.OpenInFull, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
        }
    }
}

private fun Library.thumbsOf(place: Place, vm: AppViewModel): List<Track> = when (place) {
    Place.History -> vm.historyTracks(this).distinctBy { it.albumKey }
    Place.MostPlayed -> vm.mostPlayed(this).distinctBy { it.albumKey }
    Place.Folders -> subfolders("").mapNotNull { (f, _) -> folders.entries.firstOrNull { it.key == f || it.key.startsWith("$f/") }?.value?.firstOrNull() }
    Place.Playlists -> playlists.mapNotNull { tracksOf(it).firstOrNull() }
    Place.Artists -> artists.mapNotNull { it.tracks.firstOrNull() }
    Place.Albums -> albums.map { it.tracks.first() }
    Place.Songs -> songs.distinctBy { it.albumKey }
    Place.Recent -> recentlyAdded(vm.prefs.recentDays).distinctBy { it.albumKey }
    Place.Genres -> genres.mapNotNull { it.second.firstOrNull() }
}.take(4)

/** What a section holds, in small: covers of its first playlists, artists or albums. */
@Composable
private fun PlaceThumbs(vm: AppViewModel, library: Library, place: Place, size: TileSize) {
    val thumbs = remember(library, place, vm.prefs.playsVersion) { library.thumbsOf(place, vm) }
    if (thumbs.isEmpty()) return
    val round = place == Place.Artists
    val shape = if (round) CircleShape else Radius6
    when (size) {
        TileSize.Large -> Mosaic(thumbs, Modifier.size(76.dp), 10.dp)
        TileSize.Full -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            thumbs.forEach { TrackArt(it, Modifier.size(44.dp), shape) }
        }
        TileSize.Half -> Box(Modifier.size(width = 64.dp, height = 38.dp)) {
            // A small fanned stack, the front one on the right.
            thumbs.take(3).forEachIndexed { i, track ->
                TrackArt(
                    track,
                    Modifier
                        .size(34.dp)
                        .offset(x = (i * 14).dp, y = 2.dp)
                        .graphicsLayer { rotationZ = (i - 1) * 7f }
                        .border(1.5.dp, Color.White.copy(alpha = 0.85f), shape),
                    shape,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CoverCell(title: String, subtitle: String, art: @Composable () -> Unit, vm: AppViewModel? = null, tracks: List<Track>? = null, playlist: Playlist? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.clip(Radius8).combinedClickable(onClick = onClick, onLongClick = { if (tracks != null) menu = true })) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) { art() }
            Spacer(Modifier.height(4.dp))
            Txt(title, Type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)), p.text)
            Txt(subtitle, Type.tiny, p.muted)
        }
        if (vm != null && tracks != null) CollectionMenu(vm, tracks, menu, playlist) { menu = it }
    }
}

/** Four distinct albums of a list, as Music draws a playlist's cover. */
@Composable
fun Mosaic(tracks: List<Track>, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    val picks = remember(tracks) { tracks.distinctBy { it.albumKey }.take(4) }
    val zero = RoundedCornerShape(0.dp)
    Box(modifier.clip(RoundedCornerShape(corner))) {
        if (picks.size < 4) {
            TrackArt(picks.firstOrNull(), Modifier.fillMaxSize(), zero)
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    TrackArt(picks[0], Modifier.weight(1f).fillMaxSize(), zero)
                    TrackArt(picks[1], Modifier.weight(1f).fillMaxSize(), zero)
                }
                Row(Modifier.weight(1f)) {
                    TrackArt(picks[2], Modifier.weight(1f).fillMaxSize(), zero)
                    TrackArt(picks[3], Modifier.weight(1f).fillMaxSize(), zero)
                }
            }
        }
    }
}

/** Says plainly that the Mac library was not found in the chosen folder, with the way to fix it. */
@Composable
private fun NoLibraryCard(vm: AppViewModel) {
    val picker = rememberFolderPicker(vm)
    val folder = vm.prefs.treeUri?.let { android.net.Uri.parse(it).lastPathSegment?.substringAfterLast(':') }
    Column(Modifier.fillMaxWidth().block(Blocks.Teal, LocalAppTheme.current, 16.dp).padding(12.dp)) {
        val on = Blocks.on(Blocks.Teal)
        Txt(stringResource(R.string.no_db_title), Type.tile, on, maxLines = 2)
        if (folder != null) Txt(folder, Type.small, on)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(17.dp)).background(on).clickable { picker.launch(musicFolderUri()) }.padding(horizontal = 14.dp, vertical = 7.dp),
        ) { Txt(stringResource(R.string.folder_button), Type.bodyBold, Blocks.Teal) }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, hint: String? = null) {
    val p = LocalPalette.current
    // Coming back to the app must not land in the search field with the keyboard up: the window
    // would otherwise give the field back the focus it had when the app was left.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { focusManager.clearFocus(force = true) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_START) { focusManager.clearFocus(force = true) }
    Row(
        Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(21.dp)).background(p.surface).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = p.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Txt(hint ?: stringResource(R.string.search_hint), Type.body, p.muted)
            BasicTextField(
                query,
                onChange,
                singleLine = true,
                textStyle = Type.body.copy(color = p.text),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconTap(Icons.Rounded.Close, p.muted, 18.dp, 34.dp) { onChange("") }
        }
    }
}

/** Results in place of the home sections while something is typed in the search field. */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.searchResults(vm: AppViewModel, library: Library, found: Found) {
    items(found.playlists, key = { "sp:${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { playlist ->
        PlaylistResult(vm, library, playlist)
    }
    items(found.artists, key = { "sa:${it.key}" }, span = { GridItemSpan(maxLineSpan) }) { artist ->
        val p = LocalPalette.current
        Row(
            Modifier.fillMaxWidth().clip(Radius8).combinedClickable(onClick = { vm.nav.push(Route.ArtistRoute(artist.key)) }, onLongClick = { vm.collectionMenu = artist.tracks }).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArt(artist.tracks.firstOrNull(), Modifier.size(42.dp), CircleShape)
            Spacer(Modifier.width(11.dp))
            Column {
                Txt(artist.name, Type.bodyBold)
                Txt(pluralStringResource(R.plurals.songs, artist.tracks.size, artist.tracks.size), Type.small, p.muted)
            }
        }
    }
    items(found.albums, key = { "sb:${it.key}" }, contentType = { "cover" }) { album ->
        CoverCell(album.title, album.artist, { TrackArt(album.tracks.first(), Modifier.fillMaxSize(), Radius8) }, vm, album.tracks) {
            vm.nav.push(Route.AlbumRoute(album.key))
        }
    }
    val songs = found.songs
    items(songs.size, key = { "ss:${songs[it].id}" }, span = { GridItemSpan(maxLineSpan) }) { i ->
        PickableRow(vm, Route.Root(Tab.Home), songs, i)
    }
    if (found.isEmpty) {
        item(span = { GridItemSpan(maxLineSpan) }) { Txt(stringResource(R.string.results_none), Type.body, LocalPalette.current.muted, Modifier.padding(16.dp)) }
    }
}

/** What a search finds, every kind of thing in the library. */
private class Found(val playlists: List<Playlist>, val artists: List<dev.kreal.muzon.data.Artist>, val albums: List<Album>, val songs: List<Track>) {
    val isEmpty get() = playlists.isEmpty() && artists.isEmpty() && albums.isEmpty() && songs.isEmpty()
}

private fun searchAll(library: Library, q: String): Found {
    if (Search.words(q).isEmpty()) return Found(emptyList(), emptyList(), emptyList(), emptyList())
    return Found(
        playlists = Search.filter(library.playlists, q) { Search.normalize(it.name) }.take(20),
        artists = Search.filter(library.artists, q) { Search.normalize(it.name) }.take(30),
        albums = Search.filter(library.albums, q) { Search.normalize(it.title + " " + it.artist) }.take(30),
        songs = Search.tracks(library, library.songs, q).take(300),
    )
}

@Composable
private fun PlaylistResult(vm: AppViewModel, library: Library, playlist: Playlist) {
    val p = LocalPalette.current
    val tracks = remember(playlist, library) { library.tracksOf(playlist) }
    Row(
        Modifier.fillMaxWidth().clip(Radius8).combinedClickable(onClick = { vm.nav.push(Route.PlaylistRoute(playlist.id)) }, onLongClick = {
            vm.collectionPlaylist = playlist
            vm.collectionMenu = tracks
        }).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mosaic(tracks, Modifier.size(42.dp), 6.dp)
        Spacer(Modifier.width(11.dp))
        Column {
            Txt(playlist.name, Type.bodyBold)
            Txt(stringResource(R.string.place_playlists) + " · " + pluralStringResource(R.plurals.songs, tracks.size, tracks.size), Type.small, p.muted)
        }
    }
}

/**
 * A song on a page where several can be picked: a long press starts picking, then taps pick and
 * unpick. [list] is the page's list in its order; [playlist] is the playlist whose page this is.
 */
@Composable
fun PickableRow(vm: AppViewModel, route: Route, list: List<Track>, i: Int, playlist: Playlist? = null, number: Int? = null, trailing: (@Composable () -> Unit)? = null) {
    val conn = vm.connection
    val track = list[i]
    val selection = vm.selection?.takeIf { it.route == route }
    TrackRow(
        track, conn.current?.id == track.id, conn.wantsToPlay, number = number,
        menu = { o, set -> TrackMenu(vm, track, LocalPalette.current.muted, open = o, setOpen = set, ownPlaylist = playlist?.takeIf { it.editable }?.id) },
        selected = selection?.let { track.id in it.ids },
        onLongClick = {
            val current = vm.selection?.takeIf { it.route == route }
            if (current != null) current.toggle(track.id) else vm.selection = Selection(route, playlist).also {
                it.pool = list
                it.ids.add(track.id)
            }
        },
        trailing = if (selection != null) trailing else null,
    ) {
        if (selection != null) selection.toggle(track.id) else conn.play(list, i)
    }
}

/** Keeps the picking on [route] in step with what the page shows. */
@Composable
private fun SelectionPool(vm: AppViewModel, route: Route, list: List<Track>) {
    androidx.compose.runtime.SideEffect { vm.selection?.takeIf { it.route == route }?.pool = list }
}

/** A search field over one list, which filters it as you type. */
@Composable
private fun ListFilter(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { SearchField(query, onChange, stringResource(R.string.filter_hint)) }
}

/** The music folder as it is on the phone: subfolders first, then the files in this one. */
@Composable
private fun FolderScreen(vm: AppViewModel, library: Library, route: Route.FolderRoute) {
    val path = route.path
    val p = LocalPalette.current
    val conn = vm.connection
    val subfolders = remember(library, path) { library.subfolders(path) }
    val tracks = remember(library, path) { library.folders[path].orEmpty() }
    SelectionPool(vm, route, tracks)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = statusTop() + 2.dp, bottom = LocalBottomSpace.current + 10.dp)) {
        item { PageBar(vm, p.text, if (path.isEmpty()) stringResource(R.string.place_folders) else path.substringAfterLast('/')) }
        items(subfolders, key = { "f:${it.first}" }) { (folder, count) ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.nav.push(Route.FolderRoute(folder)) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(Radius6).background(p.surfaceHigh), contentAlignment = Alignment.Center) {
                    Glyph(GlyphKind.Folders, 24.dp, Blocks.forPlace(Place.Folders))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Txt(folder.substringAfterLast('/'), Type.bodyBold, maxLines = 2)
                    Txt(pluralStringResource(R.plurals.songs, count, count), Type.small, p.muted)
                }
            }
        }
        if (tracks.isNotEmpty()) {
            item { PlayButtons(p.accent, Modifier.padding(14.dp, 8.dp), { conn.play(tracks) }, { conn.play(tracks, shuffled = true) }) }
        }
        itemsIndexed(tracks, key = { _, it -> it.id }, contentType = { _, _ -> "track" }) { i, _ ->
            PickableRow(vm, route, tracks, i)
        }
    }
}

// ---- Lists

@Composable
private fun RootTitle(text: String, modifier: Modifier = Modifier) {
    Txt(text, Type.hero, modifier = modifier.padding(horizontal = 14.dp, vertical = 6.dp))
}

@Composable
private fun PlaylistsScreen(vm: AppViewModel, library: Library) {
    val p = LocalPalette.current
    val theme = LocalAppTheme.current
    val list = rememberLazyListState()
    ScrollToTopOnTap(vm, Tab.Playlists) { list.glideToTop() }
    var filter by rememberSaveable { mutableStateOf("") }
    val shown = remember(library, filter) { Search.filter(library.playlists, filter) { Search.normalize(it.name) } }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = statusTop() + 4.dp, bottom = LocalBottomSpace.current + 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            var naming by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RootTitle(stringResource(R.string.place_playlists), Modifier.weight(1f))
                IconTap(Icons.Rounded.Add, p.text, 26.dp, 44.dp, label = stringResource(R.string.playlist_new)) { naming = true }
            }
            if (naming) {
                PlaylistNameDialog(stringResource(R.string.playlist_new), "", stringResource(R.string.create), { naming = false }) { name ->
                    naming = false
                    vm.createPlaylist(name, emptyList(), open = true, addSongs = true)
                }
            }
        }
        if (library.playlists.size > 6) item(key = "filter") { ListFilter(filter, { filter = it }) }
        itemsIndexed(shown, key = { _, it -> it.id }) { i, playlist ->
            val color = Blocks.forIndex(i)
            val on = Blocks.on(color)
            val tracks = remember(playlist) { library.tracksOf(playlist) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .block(color, theme, 16.dp)
                    .combinedClickable(onClick = { vm.nav.push(Route.PlaylistRoute(playlist.id)) }, onLongClick = {
                        vm.collectionPlaylist = playlist
                        vm.collectionMenu = tracks
                    })
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Mosaic(tracks, Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt(playlist.name, Type.tile.copy(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)), on)
                    Txt(
                        pluralStringResource(R.plurals.songs, tracks.size, tracks.size) + if (playlist.pending) " · ⟳" else "",
                        Type.small, on,
                    )
                }
            }
        }
        if (library.playlists.isEmpty()) {
            item { Txt(stringResource(R.string.no_playlists), Type.body, p.muted, Modifier.padding(14.dp), maxLines = 4) }
            item { NoLibraryCard(vm) }
        }
    }
}

@Composable
private fun ArtistsScreen(vm: AppViewModel, library: Library) {
    val p = LocalPalette.current
    val state = rememberLazyListState()
    ScrollToTopOnTap(vm, Tab.Artists) { state.glideToTop() }
    var filter by rememberSaveable { mutableStateOf("") }
    val artists = remember(library, filter) { Search.filter(library.artists, filter) { Search.normalize(it.name) } }
    val letters = remember(artists, filter) { if (filter.isBlank()) lettersOf(artists.map { it.name }) else emptyList() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(end = 18.dp, top = statusTop() + 4.dp, bottom = LocalBottomSpace.current + 10.dp)) {
            item { RootTitle(stringResource(R.string.place_artists)) }
            item(key = "filter") { ListFilter(filter, { filter = it }) }
            items(artists, key = { it.key }) { artist ->
                Row(
                    Modifier.fillMaxWidth().combinedClickable(onClick = { vm.nav.push(Route.ArtistRoute(artist.key)) }, onLongClick = { vm.collectionMenu = artist.tracks }).padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackArt(artist.tracks.firstOrNull(), Modifier.size(46.dp), CircleShape)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(artist.name, Type.bodyBold)
                        Txt(
                            pluralStringResource(R.plurals.albums, artist.albums.size, artist.albums.size) + " · " +
                                pluralStringResource(R.plurals.songs, artist.tracks.size, artist.tracks.size),
                            Type.small,
                            p.muted,
                        )
                    }
                }
            }
        }
        LetterRail(letters, state, 2, Modifier.align(Alignment.CenterEnd).padding(top = statusTop(), bottom = LocalBottomSpace.current))
    }
}

@Composable
private fun AlbumsScreen(vm: AppViewModel, library: Library) {
    val columns = vm.prefs.albumColumns
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    ScrollToTopOnTap(vm, Tab.Albums) { grid.glideToTop() }
    var filter by rememberSaveable { mutableStateOf("") }
    val albums = remember(library, filter) { Search.filter(library.albums, filter) { Search.normalize(it.title + " " + it.artist) } }
    LazyVerticalGrid(
        state = grid,
        columns = if (columns > 0) GridCells.Fixed(columns) else GridCells.Adaptive(104.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = statusTop() + 4.dp, bottom = LocalBottomSpace.current + 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { RootTitle(stringResource(R.string.place_albums)) }
        item(span = { GridItemSpan(maxLineSpan) }, key = "filter") { ListFilter(filter, { filter = it }) }
        items(albums, key = { it.key }, contentType = { "cover" }) { album ->
            CoverCell(album.title, album.artist, { TrackArt(album.tracks.first(), Modifier.fillMaxSize(), Radius8) }, vm, album.tracks) {
                vm.nav.push(Route.AlbumRoute(album.key))
            }
        }
    }
}

@Composable
private fun GenresScreen(vm: AppViewModel, library: Library) {
    val theme = LocalAppTheme.current
    val back = pageTopInset()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = back, bottom = LocalBottomSpace.current + 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PageBar(vm, LocalPalette.current.text, stringResource(R.string.place_genres)) }
        itemsIndexed(library.genres, key = { _, it -> it.first }) { i, (name, tracks) ->
            val color = Blocks.forIndex(i + 2)
            val on = Blocks.on(color)
            Row(
                Modifier.fillMaxWidth().height(66.dp).block(color, theme, 14.dp).clickable { vm.nav.push(Route.GenreRoute(name)) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(name, Type.tile, on, Modifier.weight(1f))
                Txt(tracks.size.toString(), Type.small, on)
            }
        }
    }
}

@Composable
private fun pageTopInset() = statusTop() + 2.dp

@Composable
private fun PageBar(vm: AppViewModel, tint: Color, title: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTap(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, tint, 28.dp, label = stringResource(R.string.back)) { vm.nav.pop() }
        if (title != null) Txt(title, Type.section, tint, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun SongsScreen(vm: AppViewModel, library: Library, route: Route, title: String, base: List<Track>, isRoot: Boolean) {
    val state = rememberLazyListState()
    val conn = vm.connection
    val p = LocalPalette.current
    // Every list of songs sorts the way the classic player does; each remembers its own order.
    val sortKey = "songs:" + if (isRoot) "all" else title
    val saved = remember(sortKey) { vm.prefs.sortOf(sortKey) }
    var mode by remember(sortKey) { mutableStateOf(saved.first) }
    var reversed by remember(sortKey) { mutableStateOf(saved.second) }
    var filter by rememberSaveable { mutableStateOf("") }
    val sorted = remember(base, mode, reversed) { Sorting.apply(mode, reversed, base) }
    val tracks = remember(sorted, filter, library) { Search.filter(sorted, filter) { library.searchText(it) } }
    SelectionPool(vm, route, tracks)
    // Letters only make sense while the list goes by title.
    val byTitle = mode == PlaylistSort.Title || (isRoot && mode == PlaylistSort.AsInMusic)
    if (isRoot) ScrollToTopOnTap(vm, Tab.Songs) { state.glideToTop() }
    val letters = remember(tracks, isRoot, byTitle, reversed, filter) { if (isRoot && byTitle && !reversed && filter.isBlank()) lettersOf(tracks.map { it.title }) else emptyList() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(end = if (letters.isEmpty()) 0.dp else 18.dp, top = statusTop() + 4.dp, bottom = LocalBottomSpace.current + 10.dp),
        ) {
            item {
                if (isRoot) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RootTitle(title, Modifier.weight(1f))
                        CollectionMenuButton(vm, tracks, p.text)
                    }
                } else {
                    PageBar(vm, p.text, title) { CollectionMenuButton(vm, tracks, p.text) }
                }
            }
            item(key = "filter") { ListFilter(filter, { filter = it }) }
            item {
                Txt(
                    pluralStringResource(R.plurals.songs, tracks.size, tracks.size) + " · " + stringResource(R.string.hours_short, formatHours(tracks)),
                    Type.small,
                    p.muted,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                )
            }
            item {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlayButtons(p.accent, Modifier.weight(1f), { conn.play(tracks) }, { conn.play(tracks, shuffled = true) })
                    val options = if (isRoot) PlaylistSort.entries - PlaylistSort.AsInMusic else PlaylistSort.entries
                    SortChip(if (isRoot && mode == PlaylistSort.AsInMusic) PlaylistSort.Title else mode, reversed, options, p.text, p.surfaceHigh) { m, r ->
                        mode = m
                        reversed = r
                        vm.prefs.setSort(sortKey, m, r)
                    }
                }
            }
            itemsIndexed(tracks, key = { _, it -> it.id }, contentType = { _, _ -> "track" }) { i, _ ->
                PickableRow(vm, route, tracks, i)
            }
        }
        LetterRail(letters, state, 4, Modifier.align(Alignment.CenterEnd).padding(top = statusTop(), bottom = LocalBottomSpace.current))
    }
}

/** The current order of a list, opening the choice of orders and a reverse switch. */
@Composable
private fun SortChip(mode: PlaylistSort, reversed: Boolean, options: List<PlaylistSort>, on: Color, background: Color, modifier: Modifier = Modifier, own: Boolean = false, onChange: (PlaylistSort, Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    // A playlist made here has no Music order: its own order is the one songs were added in.
    @Composable
    fun label(m: PlaylistSort) = if (own && m == PlaylistSort.AsInMusic) stringResource(R.string.sort_own) else sortLabel(m)
    Box(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(background).clickable { menu = true }.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Sort, null, tint = on, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Txt(label(mode), Type.small, on)
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = on, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), style = Type.body) },
                    leadingIcon = { if (option == mode) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) },
                    onClick = {
                        onChange(option, reversed)
                        menu = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_reverse), style = Type.body) },
                leadingIcon = { if (reversed) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) },
                onClick = {
                    onChange(mode, !reversed)
                    menu = false
                },
            )
        }
    }
}

@Composable
private fun PlayButtons(color: Color, modifier: Modifier = Modifier, onPlay: () -> Unit, onShuffle: () -> Unit, inverted: Boolean = false) {
    val theme = LocalAppTheme.current
    val on = Blocks.on(color)
    val fg = if (inverted) color else on
    val bg = if (inverted) on else color
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.height(38.dp).block(bg, theme, 19.dp).clickable(onClick = onPlay).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Txt(stringResource(R.string.play), Type.bodyBold, fg)
        }
        Row(
            Modifier.height(38.dp).block(if (inverted) on.copy(alpha = 0.18f) else LocalPalette.current.surfaceHigh, if (inverted) AppTheme.Poster else theme, 19.dp)
                .clickable(onClick = onShuffle).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Shuffle, null, tint = if (inverted) on else LocalPalette.current.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Txt(stringResource(R.string.shuffle), Type.bodyBold, if (inverted) on else LocalPalette.current.text)
        }
    }
}

// ---- Details

@Composable
private fun sortLabel(mode: PlaylistSort) = stringResource(
    when (mode) {
        PlaylistSort.AsInMusic -> R.string.sort_as_music
        PlaylistSort.Artist -> R.string.sort_artist
        PlaylistSort.Album -> R.string.sort_album
        PlaylistSort.Title -> R.string.sort_title
        PlaylistSort.Genre -> R.string.sort_genre
        PlaylistSort.DateAdded -> R.string.sort_date
        PlaylistSort.Duration -> R.string.sort_duration
        PlaylistSort.Year -> R.string.sort_year
    },
)

@Composable
private fun PlaylistScreen(vm: AppViewModel, library: Library, route: Route, playlist: Playlist) {
    val conn = vm.connection
    val p = LocalPalette.current
    val index = library.playlists.indexOf(playlist)
    val color = Blocks.forIndex(index.coerceAtLeast(0))
    val on = Blocks.on(color)
    val base = remember(playlist, library) { library.tracksOf(playlist) }
    val saved = remember(playlist.id) { vm.prefs.sortOf(playlist.id) }
    var mode by remember(playlist.id) { mutableStateOf(saved.first) }
    var reversed by remember(playlist.id) { mutableStateOf(saved.second) }
    var filter by rememberSaveable { mutableStateOf("") }
    val sorted = remember(base, mode, reversed) { Sorting.apply(mode, reversed, base) }
    val tracks = remember(sorted, filter, library) { Search.filter(sorted, filter) { library.searchText(it) } }
    SelectionPool(vm, route, tracks)
    val picking = vm.selection?.route == route
    // Songs are dragged into place while picking, in the playlist's own order and with no filter.
    val canDrag = playlist.editable && picking && mode == PlaylistSort.AsInMusic && !reversed && filter.isBlank()
    var order by remember(tracks) { mutableStateOf(tracks) }
    val state = rememberLazyListState()
    val reorder = sh.calvin.reorderable.rememberReorderableLazyListState(state) { from, to ->
        val f = order.indexOfFirst { it.id == from.key }
        val t = order.indexOfFirst { it.id == to.key }
        if (f >= 0 && t >= 0) order = order.toMutableList().apply { add(t, removeAt(f)) }
    }
    val shown = if (canDrag) order else tracks
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomSpace.current + 10.dp)) {
        item(key = "head") {
            Column(Modifier.fillMaxWidth().block(color, LocalAppTheme.current, 0.dp).padding(top = statusTop(), bottom = 12.dp)) {
                PageBar(vm, on) {
                    if (playlist.editable) EditPlaylistMenu(vm, playlist, on)
                    CollectionMenuButton(vm, tracks, on, playlist)
                    SortChip(mode, reversed, PlaylistSort.entries, on, Color.Black.copy(alpha = 0.18f), Modifier.padding(end = 10.dp), own = playlist.own) { m, r ->
                        mode = m
                        reversed = r
                        vm.prefs.setSort(playlist.id, m, r)
                    }
                }
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Mosaic(base, Modifier.size(84.dp), 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Txt(playlist.name, Type.hero, on, maxLines = 2)
                        Txt(
                            pluralStringResource(R.plurals.songs, base.size, base.size) + " · " +
                                stringResource(R.string.hours_short, formatHours(base)),
                            Type.small,
                            on,
                        )
                        if (playlist.pending) Txt("⟳ " + stringResource(R.string.pending_sync), Type.tiny, on, maxLines = 2)
                        else if (playlist.smart) Txt(stringResource(R.string.smart_note), Type.tiny, on, maxLines = 2)
                    }
                }
                Row(Modifier.padding(start = 14.dp, top = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlayButtons(color, Modifier.weight(1f), { conn.play(tracks) }, { conn.play(tracks, shuffled = true) }, inverted = true)
                }
                if (playlist.editable || tracks.isNotEmpty()) {
                    Row(Modifier.padding(start = 14.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (playlist.editable) HeaderChip(Icons.Rounded.Add, stringResource(R.string.add_songs), on) { vm.nav.push(Route.AddSongsRoute(playlist.id)) }
                        if (tracks.isNotEmpty()) HeaderChip(Icons.Rounded.Check, stringResource(R.string.select), on) {
                            vm.selection = if (picking) null else Selection(route, playlist).also { it.pool = tracks }
                        }
                    }
                }
            }
        }
        if (base.size > 8) item(key = "filter") { ListFilter(filter, { filter = it }, Modifier.padding(top = 6.dp)) }
        itemsIndexed(shown, key = { _, it -> it.id }, contentType = { _, _ -> "track" }) { i, track ->
            ReorderableItem(reorder, key = track.id, enabled = canDrag) { dragging ->
                Box(Modifier.background(if (dragging) p.surfaceHigh else Color.Transparent)) {
                    PickableRow(vm, route, shown, i, playlist, trailing = if (!canDrag) null else ({
                        Icon(
                            Icons.Rounded.DragHandle, stringResource(R.string.move_top), tint = p.muted,
                            modifier = Modifier.padding(start = 6.dp).size(26.dp).draggableHandle(
                                onDragStopped = { vm.reorderPlaylist(playlist.id, order.map { it.id }) },
                            ),
                        )
                    }))
                }
            }
        }
        if (playlist.editable && base.isEmpty()) {
            item {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Txt(stringResource(R.string.playlist_empty), Type.body, p.muted, maxLines = 4)
                    Box(Modifier.clip(RoundedCornerShape(19.dp)).background(p.accent).clickable { vm.nav.push(Route.AddSongsRoute(playlist.id)) }.padding(horizontal = 16.dp, vertical = 9.dp)) {
                        Txt("+ " + stringResource(R.string.add_songs), Type.bodyBold, Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, on: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.18f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = on, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Txt(text, Type.small, on)
    }
}

/** Rename and delete, for a playlist that can be changed here. */
@Composable
private fun EditPlaylistMenu(vm: AppViewModel, playlist: Playlist, tint: Color) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconTap(Icons.Rounded.Edit, tint, 22.dp, 44.dp, label = stringResource(R.string.playlist_rename)) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.add_songs), style = Type.body) }, leadingIcon = { Icon(Icons.Rounded.Add, null) }, onClick = {
                open = false
                vm.nav.push(Route.AddSongsRoute(playlist.id))
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.playlist_rename), style = Type.body) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = {
                open = false
                vm.renamingPlaylist = playlist
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.playlist_delete), style = Type.body) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = {
                open = false
                vm.deletingPlaylist = playlist
            })
        }
    }
}

/**
 * Picks songs to add to [playlist]: every song of the library, with a search, those already in it
 * shown ticked. The button adds the picked ones at the end, in the order they were picked.
 */
@Composable
private fun AddSongsScreen(vm: AppViewModel, library: Library, playlist: Playlist) {
    val p = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    val picked = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val inside = remember(playlist) { playlist.trackIds.toHashSet() }
    val songs = remember(library, query) { if (query.isBlank()) library.songs else Search.tracks(library, library.songs, query) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = statusTop() + 2.dp, bottom = LocalBottomSpace.current + 70.dp)) {
            item { PageBar(vm, p.text, stringResource(R.string.add_to_title, playlist.name)) }
            item(key = "filter") { ListFilter(query, { query = it }) }
            items(songs, key = { it.id }, contentType = { "track" }) { track ->
                val already = track.id in inside
                Box(Modifier.graphicsLayer { alpha = if (already) 0.5f else 1f }) {
                    TrackRow(track, false, false, selected = already || track.id in picked, onLongClick = {}) {
                        if (!already) { if (track.id in picked) picked.remove(track.id) else picked.add(track.id) }
                    }
                }
            }
            if (songs.isEmpty()) item { Txt(stringResource(R.string.results_none), Type.body, p.muted, Modifier.padding(16.dp)) }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomSpace.current + 10.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (picked.isEmpty()) p.surfaceHigh else p.accent)
                .clickable(enabled = picked.isNotEmpty()) {
                    vm.addToPlaylist(playlist.id, picked.mapNotNull(library.byId::get))
                    vm.nav.pop()
                }
                .padding(horizontal = 26.dp, vertical = 13.dp),
        ) {
            Txt(stringResource(R.string.add_count, picked.size), Type.bodyBold, if (picked.isEmpty()) p.muted else Color.White)
        }
    }
}

@Composable
private fun ArtistScreen(vm: AppViewModel, library: Library, route: Route, artist: dev.kreal.muzon.data.Artist) {
    val conn = vm.connection
    var filter by rememberSaveable { mutableStateOf("") }
    val tracks = remember(artist, filter, library) { Search.filter(artist.tracks, filter) { library.searchText(it) } }
    SelectionPool(vm, route, tracks)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = statusTop() + 2.dp, bottom = LocalBottomSpace.current + 10.dp)) {
        item { PageBar(vm, LocalPalette.current.text) { CollectionMenuButton(vm, artist.tracks, LocalPalette.current.text) } }
        item { Txt(artist.name, Type.hero, modifier = Modifier.padding(horizontal = 14.dp), maxLines = 2) }
        item { PlayButtons(LocalPalette.current.accent, Modifier.padding(14.dp, 8.dp), { conn.play(artist.tracks) }, { conn.play(artist.tracks, shuffled = true) }) }
        if (artist.albums.size > 1 || artist.albums.firstOrNull()?.tracks?.size != artist.tracks.size) {
            item {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(artist.albums, key = { it.key }) { album ->
                        Box(Modifier.width(112.dp)) {
                            CoverCell(album.title, album.year.takeIf { it > 0 }?.toString().orEmpty(), { TrackArt(album.tracks.first(), Modifier.fillMaxSize(), Radius8) }, vm, album.tracks) {
                                vm.nav.push(Route.AlbumRoute(album.key))
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        if (artist.tracks.size > 8) item(key = "filter") { ListFilter(filter, { filter = it }) }
        itemsIndexed(tracks, key = { _, it -> it.id }, contentType = { _, _ -> "track" }) { i, _ ->
            PickableRow(vm, route, tracks, i)
        }
    }
}

@Composable
private fun AlbumScreen(vm: AppViewModel, route: Route, album: Album) {
    val p = LocalPalette.current
    val conn = vm.connection
    SelectionPool(vm, route, album.tracks)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = statusTop() + 2.dp, bottom = LocalBottomSpace.current + 10.dp)) {
        item { PageBar(vm, p.text) { CollectionMenuButton(vm, album.tracks, p.text) } }
        item {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                TrackArt(album.tracks.first(), Modifier.size(108.dp), Radius12, large = false)
                Spacer(Modifier.width(12.dp))
                Column {
                    Txt(album.title, Type.section, maxLines = 3)
                    Spacer(Modifier.height(3.dp))
                    Txt(album.artist, Type.body, p.muted)
                    // Year, number of songs and total length, as the classic player shows them.
                    Txt(
                        listOfNotNull(
                            album.year.takeIf { it > 0 }?.toString(),
                            pluralStringResource(R.plurals.songs, album.tracks.size, album.tracks.size),
                            formatLong(album.tracks.sumOf { it.durationMs }),
                        ).joinToString(" · "),
                        Type.small,
                        p.muted,
                    )
                }
            }
        }
        item { PlayButtons(p.accent, Modifier.padding(14.dp, 10.dp), { conn.play(album.tracks) }, { conn.play(album.tracks, shuffled = true) }) }
        itemsIndexed(album.tracks, key = { _, it -> it.id }, contentType = { _, _ -> "track" }) { i, _ ->
            PickableRow(vm, route, album.tracks, i)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchScreen(vm: AppViewModel, library: Library) {
    val p = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val found = remember(query, library) { searchAll(library, query) }
    SelectionPool(vm, Route.SearchRoute, found.songs)
    Column(Modifier.fillMaxSize().padding(top = statusTop())) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, p.text, 28.dp) { vm.nav.pop() }
            Row(
                Modifier.weight(1f).padding(end = 12.dp).height(38.dp).clip(RoundedCornerShape(19.dp)).background(p.surface).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = p.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Txt(stringResource(R.string.search_hint), Type.body, p.muted)
                    BasicTextField(
                        query,
                        { query = it },
                        singleLine = true,
                        textStyle = Type.body.copy(color = p.text),
                        cursorBrush = SolidColor(p.accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomSpace.current + 10.dp)) {
            items(found.playlists, key = { "pl:${it.id}" }) { Box(Modifier.padding(horizontal = 14.dp)) { PlaylistResult(vm, library, it) } }
            items(found.artists, key = { "ar:${it.key}" }) { artist ->
                Row(
                    Modifier.fillMaxWidth().combinedClickable(onClick = { vm.nav.push(Route.ArtistRoute(artist.key)) }, onLongClick = { vm.collectionMenu = artist.tracks }).padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackArt(artist.tracks.firstOrNull(), Modifier.size(42.dp), CircleShape)
                    Spacer(Modifier.width(11.dp))
                    Txt(artist.name, Type.bodyBold)
                }
            }
            items(found.albums, key = { "al:${it.key}" }) { album ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.nav.push(Route.AlbumRoute(album.key)) }.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackArt(album.tracks.first(), Modifier.size(42.dp), Radius6)
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Txt(album.title, Type.bodyBold)
                        Txt(album.artist, Type.small, p.muted)
                    }
                }
            }
            itemsIndexed(found.songs, key = { _, it -> "s:${it.id}" }) { i, _ ->
                PickableRow(vm, Route.SearchRoute, found.songs, i)
            }
            if (query.isNotBlank() && found.isEmpty) {
                item { Txt(stringResource(R.string.results_none), Type.body, p.muted, Modifier.padding(16.dp)) }
            }
        }
    }
}
