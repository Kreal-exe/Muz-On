package dev.kreal.muzon.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
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
import dev.kreal.muzon.data.Place
import dev.kreal.muzon.data.Playlist
import dev.kreal.muzon.data.PlaylistSort
import dev.kreal.muzon.data.Sorting
import dev.kreal.muzon.data.Track

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
            Tab.Songs -> SongsScreen(vm, stringResource(R.string.place_songs), library.songs, true)
        }
        is Route.PlaylistRoute -> library.playlists.firstOrNull { it.id == route.id }?.let { PlaylistScreen(vm, library, it) }
        is Route.ArtistRoute -> library.artistsByKey[route.artistKey]?.let { ArtistScreen(vm, it) }
        is Route.AlbumRoute -> library.albumsByKey[route.albumKey]?.let { AlbumScreen(vm, it) }
        is Route.GenreRoute -> library.genres.firstOrNull { it.first == route.name }?.let { SongsScreen(vm, it.first, it.second, false) }
        Route.RecentRoute -> SongsScreen(vm, stringResource(R.string.place_recent), library.recentlyAdded, false)
        Route.GenresRoute -> GenresScreen(vm, library)
        Route.SearchRoute -> SearchScreen(vm, library)
        Route.SettingsRoute -> SettingsScreen(vm, library)
    }
}

private fun Place.glyph() = when (this) {
    Place.Playlists -> GlyphKind.Playlists
    Place.Artists -> GlyphKind.Artists
    Place.Albums -> GlyphKind.Albums
    Place.Songs -> GlyphKind.Songs
    Place.Recent -> GlyphKind.Recent
    Place.Genres -> GlyphKind.Genres
}

@Composable
private fun Place.label() = stringResource(
    when (this) {
        Place.Playlists -> R.string.place_playlists
        Place.Artists -> R.string.place_artists
        Place.Albums -> R.string.place_albums
        Place.Songs -> R.string.place_songs
        Place.Recent -> R.string.place_recent
        Place.Genres -> R.string.place_genres
    },
)

private fun Library.count(place: Place) = when (place) {
    Place.Playlists -> playlists.size
    Place.Artists -> artists.size
    Place.Albums -> albums.size
    Place.Songs -> tracks.size
    Place.Recent -> recentlyAdded.size
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
    }
}

// ---- Home

@Composable
private fun HomeScreen(vm: AppViewModel, library: Library) {
    val p = LocalPalette.current
    var editing by rememberSaveable { mutableStateOf(false) }
    val theme = LocalAppTheme.current
    val places = vm.prefs.places
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = statusTop() + 6.dp, bottom = LocalBottomSpace.current + 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "places", span = { GridItemSpan(maxLineSpan) }, contentType = "places") {
            if (editing) PlacesEditor(vm) else PlaceTiles(vm, library, places, theme)
        }
        item(key = "actions", span = { GridItemSpan(maxLineSpan) }, contentType = "actions") {
            Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.nav.push(Route.SearchRoute) }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(GlyphKind.Search, 18.dp, p.muted)
                    Spacer(Modifier.width(5.dp))
                    Txt(stringResource(R.string.search), Type.small, p.muted)
                }
                Spacer(Modifier.weight(1f))
                if (editing) {
                    IconTap(Icons.Rounded.Settings, p.muted, 20.dp, 34.dp) { vm.nav.push(Route.SettingsRoute) }
                }
                Tappable({ editing = !editing }, Modifier.clip(RoundedCornerShape(8.dp)).padding(4.dp)) {
                    Txt(stringResource(if (editing) R.string.done else R.string.edit), Type.small, if (editing) p.accent else p.muted)
                }
            }
        }
        items(library.playlists, key = { "p:${it.id}" }, contentType = { "cover" }) { playlist ->
            CoverCell(playlist.name, library.tracksOf(playlist).size.toString(), { Mosaic(library.tracksOf(playlist), Modifier.fillMaxSize()) }) {
                vm.nav.push(Route.PlaylistRoute(playlist.id))
            }
        }
        items(library.albums, key = { "a:${it.key}" }, contentType = { "cover" }) { album ->
            CoverCell(album.title, album.artist, { TrackArt(album.tracks.first(), Modifier.fillMaxSize(), Radius8) }) {
                vm.nav.push(Route.AlbumRoute(album.key))
            }
        }
    }
}

@Composable
private fun PlaceTiles(vm: AppViewModel, library: Library, places: List<Place>, theme: AppTheme) {
    if (places.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        places.firstOrNull()?.let { first ->
            val color = Blocks.forPlace(first)
            val on = Blocks.on(color)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .block(color, theme, 18.dp)
                    .clickable { vm.openPlace(first) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Txt(first.label(), Type.section.copy(fontSize = androidx.compose.ui.unit.TextUnit(19f, androidx.compose.ui.unit.TextUnitType.Sp)), on)
                    Txt(library.count(first).toString(), Type.small, on)
                }
                if (first == Place.Playlists && library.playlists.isNotEmpty()) {
                    Mosaic(library.tracksOf(library.playlists.first()), Modifier.size(64.dp))
                } else {
                    Glyph(first.glyph(), 44.dp, on)
                }
            }
        }
        places.drop(1).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { place ->
                    val color = Blocks.forPlace(place)
                    val on = Blocks.on(color)
                    Row(
                        Modifier
                            .weight(1f)
                            .height(70.dp)
                            .block(color, theme, 16.dp)
                            .clickable { vm.openPlace(place) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Txt(place.label(), Type.tile, on)
                            Txt(library.count(place).toString(), Type.small, on)
                        }
                        Glyph(place.glyph(), 30.dp, on)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Shown places first, in order, each draggable by its handle; hidden ones below. */
@Composable
private fun PlacesEditor(vm: AppViewModel) {
    val p = LocalPalette.current
    val shown = vm.prefs.places
    val hidden = Place.entries.filter { it !in shown }
    val density = LocalDensity.current
    val rowPx = with(density) { 48.dp.toPx() }
    var dragging by remember { mutableStateOf<Place?>(null) }
    val dragOffset = remember { mutableFloatStateOf(0f) }
    Column(Modifier.clip(Radius16).background(p.surface).padding(horizontal = 12.dp)) {
        shown.forEach { place ->
            val isDragged = dragging == place
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging == place) dragOffset.floatValue else 0f }
                    .background(p.surface),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Blocks.forPlace(place)))
                Spacer(Modifier.width(12.dp))
                Txt(place.label(), Type.bodyBold, modifier = Modifier.weight(1f))
                IconTap(Icons.Rounded.Visibility, p.muted, 20.dp, 40.dp) {
                    if (shown.size > 1) vm.prefs.updatePlaces(shown - place)
                }
                Box(
                    Modifier.size(40.dp).pointerInput(place) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragging = place; dragOffset.floatValue = 0f },
                            onDragEnd = { dragging = null; dragOffset.floatValue = 0f },
                            onDragCancel = { dragging = null; dragOffset.floatValue = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset.floatValue += amount.y
                                val current = vm.prefs.places
                                val index = current.indexOf(place)
                                val move = (dragOffset.floatValue / rowPx).toInt()
                                if (move != 0) {
                                    val target = (index + move).coerceIn(0, current.lastIndex)
                                    if (target != index) {
                                        vm.prefs.updatePlaces(current.toMutableList().apply { add(target, removeAt(index)) })
                                        dragOffset.floatValue -= (target - index) * rowPx
                                    }
                                }
                            },
                        )
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.DragHandle, null, tint = p.muted) }
            }
        }
        hidden.forEach { place ->
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(p.surfaceHigh))
                Spacer(Modifier.width(12.dp))
                Txt(place.label(), Type.body, p.muted, Modifier.weight(1f))
                IconTap(Icons.Rounded.VisibilityOff, p.muted, 20.dp, 40.dp) { vm.prefs.updatePlaces(shown + place) }
                Spacer(Modifier.width(40.dp))
            }
        }
    }
}

@Composable
private fun CoverCell(title: String, subtitle: String, art: @Composable () -> Unit, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.clip(Radius8).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) { art() }
        Spacer(Modifier.height(4.dp))
        Txt(title, Type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)), p.text)
        Txt(subtitle, Type.tiny, p.muted)
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

// ---- Lists

@Composable
private fun RootTitle(text: String, modifier: Modifier = Modifier) {
    Txt(text, Type.hero, modifier = modifier.padding(horizontal = 14.dp, vertical = 6.dp))
}

@Composable
private fun PlaylistsScreen(vm: AppViewModel, library: Library) {
    val p = LocalPalette.current
    val theme = LocalAppTheme.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = statusTop() + 4.dp, bottom = LocalBottomSpace.current + 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { RootTitle(stringResource(R.string.place_playlists)) }
        itemsIndexed(library.playlists, key = { _, it -> it.id }) { i, playlist ->
            val color = Blocks.forIndex(i)
            val on = Blocks.on(color)
            val tracks = remember(playlist) { library.tracksOf(playlist) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .block(color, theme, 16.dp)
                    .clickable { vm.nav.push(Route.PlaylistRoute(playlist.id)) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Mosaic(tracks, Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt(playlist.name, Type.tile.copy(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)), on)
                    Txt(pluralStringResource(R.plurals.songs, tracks.size, tracks.size), Type.small, on)
                }
            }
        }
        if (library.playlists.isEmpty()) {
            item { Txt(stringResource(R.string.no_playlists), Type.body, p.muted, Modifier.padding(14.dp), maxLines = 4) }
        }
    }
}

@Composable
private fun ArtistsScreen(vm: AppViewModel, library: Library) {
    val p = LocalPalette.current
    val state = rememberLazyListState()
    val letters = remember(library) { lettersOf(library.artists.map { it.name }) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(end = 18.dp, top = statusTop() + 4.dp, bottom = LocalBottomSpace.current + 10.dp)) {
            item { RootTitle(stringResource(R.string.place_artists)) }
            items(library.artists, key = { it.key }) { artist ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.nav.push(Route.ArtistRoute(artist.key)) }.padding(horizontal = 14.dp, vertical = 6.dp),
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
        LetterRail(letters, state, 1, Modifier.align(Alignment.CenterEnd).padding(top = statusTop(), bottom = LocalBottomSpace.current))
    }
}

@Composable
private fun AlbumsScreen(vm: AppViewModel, library: Library) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = statusTop() + 4.dp, bottom = LocalBottomSpace.current + 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { RootTitle(stringResource(R.string.place_albums)) }
        items(library.albums, key = { it.key }, contentType = { "cover" }) { album ->
            CoverCell(album.title, album.artist, { TrackArt(album.tracks.first(), Modifier.fillMaxSize(), Radius8) }) {
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
private fun SongsScreen(vm: AppViewModel, title: String, tracks: List<Track>, isRoot: Boolean) {
    val state = rememberLazyListState()
    val conn = vm.connection
    val letters = remember(tracks, isRoot) { if (isRoot) lettersOf(tracks.map { it.title }) else emptyList() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(end = if (letters.isEmpty()) 0.dp else 18.dp, top = statusTop() + 4.dp, bottom = LocalBottomSpace.current + 10.dp),
        ) {
            item {
                if (isRoot) RootTitle(title) else PageBar(vm, LocalPalette.current.text, title)
            }
            item { PlayButtons(LocalPalette.current.accent, Modifier.padding(horizontal = 14.dp, vertical = 4.dp), { conn.play(tracks) }, { conn.play(tracks, shuffled = true) }) }
            itemsIndexed(tracks, key = { _, it -> it.id }, contentType = { _, _ -> "track" }) { i, track ->
                TrackRow(track, conn.current?.id == track.id, conn.wantsToPlay) { conn.play(tracks, i) }
            }
        }
        LetterRail(letters, state, 2, Modifier.align(Alignment.CenterEnd).padding(top = statusTop(), bottom = LocalBottomSpace.current))
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
    },
)

@Composable
private fun PlaylistScreen(vm: AppViewModel, library: Library, playlist: Playlist) {
    val conn = vm.connection
    val index = library.playlists.indexOf(playlist)
    val color = Blocks.forIndex(index.coerceAtLeast(0))
    val on = Blocks.on(color)
    val base = remember(playlist) { library.tracksOf(playlist) }
    val saved = remember(playlist.id) { vm.prefs.sortOf(playlist.id) }
    var mode by remember(playlist.id) { mutableStateOf(saved.first) }
    var reversed by remember(playlist.id) { mutableStateOf(saved.second) }
    val tracks = remember(base, mode, reversed) { Sorting.apply(mode, reversed, base) }
    var menu by remember { mutableStateOf(false) }
    val state = rememberLazyListState()
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomSpace.current + 10.dp)) {
        item(key = "head") {
            Column(Modifier.fillMaxWidth().block(color, LocalAppTheme.current, 0.dp).padding(top = statusTop(), bottom = 12.dp)) {
                PageBar(vm, on) {
                    Box {
                        Row(
                            Modifier.padding(end = 10.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.18f))
                                .clickable { menu = true }.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Txt(sortLabel(mode), Type.small, on)
                            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = on, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            PlaylistSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(sortLabel(option), style = Type.body) },
                                    leadingIcon = { if (option == mode) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) },
                                    onClick = {
                                        mode = option
                                        vm.prefs.setSort(playlist.id, mode, reversed)
                                        menu = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_reverse), style = Type.body) },
                                leadingIcon = { if (reversed) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) },
                                onClick = {
                                    reversed = !reversed
                                    vm.prefs.setSort(playlist.id, mode, reversed)
                                    menu = false
                                },
                            )
                        }
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
                    }
                }
                PlayButtons(color, Modifier.padding(start = 14.dp, top = 10.dp), { conn.play(tracks) }, { conn.play(tracks, shuffled = true) }, inverted = true)
            }
        }
        itemsIndexed(tracks, key = { _, it -> it.id }, contentType = { _, _ -> "track" }) { i, track ->
            TrackRow(track, conn.current?.id == track.id, conn.wantsToPlay) { conn.play(tracks, i) }
        }
    }
}

@Composable
private fun ArtistScreen(vm: AppViewModel, artist: dev.kreal.muzon.data.Artist) {
    val conn = vm.connection
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = statusTop() + 2.dp, bottom = LocalBottomSpace.current + 10.dp)) {
        item { PageBar(vm, LocalPalette.current.text) }
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
                            CoverCell(album.title, album.year.takeIf { it > 0 }?.toString().orEmpty(), { TrackArt(album.tracks.first(), Modifier.fillMaxSize(), Radius8) }) {
                                vm.nav.push(Route.AlbumRoute(album.key))
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        itemsIndexed(artist.tracks, key = { _, it -> it.id }, contentType = { _, _ -> "track" }) { i, track ->
            TrackRow(track, conn.current?.id == track.id, conn.wantsToPlay) { conn.play(artist.tracks, i) }
        }
    }
}

@Composable
private fun AlbumScreen(vm: AppViewModel, album: Album) {
    val p = LocalPalette.current
    val conn = vm.connection
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = statusTop() + 2.dp, bottom = LocalBottomSpace.current + 10.dp)) {
        item { PageBar(vm, p.text) }
        item {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                TrackArt(album.tracks.first(), Modifier.size(108.dp), Radius12, large = false)
                Spacer(Modifier.width(12.dp))
                Column {
                    Txt(album.title, Type.section, maxLines = 3)
                    Spacer(Modifier.height(3.dp))
                    Txt(album.artist, Type.body, p.muted)
                    if (album.year > 0) Txt(album.year.toString(), Type.small, p.muted)
                }
            }
        }
        item { PlayButtons(p.accent, Modifier.padding(14.dp, 10.dp), { conn.play(album.tracks) }, { conn.play(album.tracks, shuffled = true) }) }
        itemsIndexed(album.tracks, key = { _, it -> it.id }, contentType = { _, _ -> "track" }) { i, track ->
            TrackRow(track, conn.current?.id == track.id, conn.wantsToPlay) { conn.play(album.tracks, i) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchScreen(vm: AppViewModel, library: Library) {
    val p = LocalPalette.current
    val conn = vm.connection
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val q = query.trim().lowercase()
    val songs = remember(q, library) {
        if (q.isEmpty()) emptyList() else library.songs.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q) }.take(200)
    }
    val artists = remember(q, library) { if (q.isEmpty()) emptyList() else library.artists.filter { it.name.lowercase().contains(q) } }
    val albums = remember(q, library) { if (q.isEmpty()) emptyList() else library.albums.filter { it.title.lowercase().contains(q) }.take(30) }
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
            items(artists, key = { "ar:${it.key}" }) { artist ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.nav.push(Route.ArtistRoute(artist.key)) }.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackArt(artist.tracks.firstOrNull(), Modifier.size(42.dp), CircleShape)
                    Spacer(Modifier.width(11.dp))
                    Txt(artist.name, Type.bodyBold)
                }
            }
            items(albums, key = { "al:${it.key}" }) { album ->
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
            itemsIndexed(songs, key = { _, it -> "s:${it.id}" }) { i, track ->
                TrackRow(track, conn.current?.id == track.id, conn.wantsToPlay) { conn.play(songs, i) }
            }
            if (q.isNotEmpty() && songs.isEmpty() && artists.isEmpty() && albums.isEmpty()) {
                item { Txt(stringResource(R.string.results_none), Type.body, p.muted, Modifier.padding(16.dp)) }
            }
        }
    }
}
