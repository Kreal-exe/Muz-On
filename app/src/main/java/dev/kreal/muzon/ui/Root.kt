package dev.kreal.muzon.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kreal.muzon.R
import dev.kreal.muzon.data.AppTheme
import dev.kreal.muzon.data.Library
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.flow.first

private val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun AppRoot() {
    val vm: AppViewModel = viewModel()
    val context = LocalContext.current
    AppThemeProvider(vm.prefs.theme, vm.prefs.colorMode, vm.prefs.accent) {
        val p = LocalPalette.current
        var hasAudio by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED)
        }
        val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            hasAudio = result[audioPermission] == true
        }
        val view = LocalView.current
        // The display's top rate, whatever it is: 120, 144 or more. A rate category is not enough here:
        // this phone maps "high" to 90 Hz, so the vote names the number.
        val topRate = LocalConfiguration.current.let { _ ->
            view.display?.supportedModes?.filter { it.physicalWidth == view.display.mode.physicalWidth }?.maxOfOrNull { it.refreshRate } ?: 120f
        }
        if (Build.VERSION.SDK_INT >= 35) {
            SideEffect { view.requestedFrameRate = topRate }
        }
        LaunchedEffect(topRate) { vm.topFrameRate = topRate }
        val light = !p.isDark
        // Dark clock and icons on a light page, light ones over the open player. Read in a flow:
        // a SideEffect does not watch the sheet, so the icons used to stay white after it closed.
        LaunchedEffect(light, view) {
            val window = (view.context as android.app.Activity).window
            androidx.compose.runtime.snapshotFlow { light && vm.sheet <= 0.5f }.collect { dark ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = dark
                    isAppearanceLightNavigationBars = dark
                }
            }
        }
        // Without this the phone falls back to 60 Hz the moment a finger lifts: whatever the
        // display's top rate is, 120 or 144, every animation gets it.
        Box(Modifier.fillMaxSize().preferredFrameRate(topRate).background(p.background)) {
            when {
                !hasAudio -> Welcome(
                    title = stringResource(R.string.grant_title),
                    text = stringResource(R.string.grant_text),
                    button = stringResource(R.string.grant_button),
                ) {
                    val wanted = buildList {
                        add(audioPermission)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissions.launch(wanted.toTypedArray())
                }
                vm.prefs.treeUri == null && !vm.prefs.folderSkipped -> FolderStep(vm)
                else -> Loaded(vm)
            }
        }
    }
}

@Composable
private fun Welcome(title: String, text: String, button: String, skip: String? = null, onSkip: () -> Unit = {}, onClick: () -> Unit) {
    val p = LocalPalette.current
    val theme = LocalAppTheme.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.fillMaxWidth().block(Blocks.Orange, theme, 22.dp).padding(20.dp)) {
            Txt(title, Type.hero, Color.White, maxLines = 3)
            Spacer(Modifier.height(10.dp))
            Txt(text, Type.body, Color.White, maxLines = 8)
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.block(Color.White, theme, 20.dp).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 11.dp),
            ) { Txt(button, Type.bodyBold, Blocks.Orange) }
        }
        if (skip != null) {
            Spacer(Modifier.height(10.dp))
            Tappable(onSkip, Modifier.clip(RoundedCornerShape(8.dp)).padding(10.dp)) { Txt(skip, Type.body, p.muted, maxLines = 2) }
        }
    }
}

@Composable
private fun FolderStep(vm: AppViewModel) {
    val picker = rememberFolderPicker(vm)
    Welcome(
        title = stringResource(R.string.folder_title),
        text = stringResource(R.string.folder_text),
        button = stringResource(R.string.folder_button),
        skip = stringResource(R.string.folder_skip),
        onSkip = { vm.prefs.skipFolder() },
    ) { picker.launch(musicFolderUri()) }
}

@Composable
private fun Loaded(vm: AppViewModel) {
    vm.nav.scope = rememberCoroutineScope()
    val library by vm.repository.library.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        vm.connection.connect()
        vm.refreshLibrary()
    }
    val current = library ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        SideEffect { vm.isWide = wide }
        // Tabs at the bottom on every screen, as in a tablet music app; only the open player splits.
        CompactShell(vm, current)
    }
}

// ---- Cover screen: tabs at the bottom, mini player above them

@Composable
private fun CompactShell(vm: AppViewModel, library: Library) {
    val holder = rememberSaveableStateHolder()
    val p = LocalPalette.current
    // Kept clear of the system's gesture strip at the bottom edge, where a sideways drag switches apps.
    val bottom = maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), 14.dp)
    val tabsHeight = 64.dp + bottom
    val hasTrack = vm.connection.current != null
    val space = tabsHeight + if (hasTrack) 66.dp else 6.dp

    BackHandling(vm)
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBottomSpace provides space) {
            TabPager(vm, holder, Modifier.graphicsLayer { alpha = if (vm.sheet >= 1f) 0f else 1f }) { route -> RouteContent(route, vm, library) }
        }
        if (hasTrack) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(tabsHeight + 36.dp)
                    .graphicsLayer { translationY = vm.sheet * (tabsHeight + 36.dp).toPx() }
                    .background(p.background),
            )
        }
        CompactPlayer(vm, tabsHeight)
        vm.addToPlaylist?.let { tracks -> AddToPlaylistDialog(vm, tracks) { vm.addToPlaylist = null } }
        vm.renamingPlaylist?.let { pl ->
            PlaylistNameDialog(stringResource(R.string.playlist_rename), pl.name, stringResource(R.string.done), { vm.renamingPlaylist = null }) { name ->
                vm.renamingPlaylist = null
                vm.renamePlaylist(pl.id, name)
            }
        }
        vm.deletingPlaylist?.let { pl ->
            ConfirmDialog(stringResource(R.string.playlist_delete), stringResource(R.string.playlist_delete_confirm, pl.name), stringResource(R.string.delete), { vm.deletingPlaylist = null }) {
                vm.deletingPlaylist = null
                vm.deletePlaylist(pl.id)
            }
        }
        vm.collectionMenu?.let { tracks ->
            Box(Modifier.align(Alignment.Center)) {
                CollectionMenu(vm, tracks, true, vm.collectionPlaylist) {
                    if (!it) {
                        vm.collectionMenu = null
                        vm.collectionPlaylist = null
                    }
                }
            }
        }
        TabBar(
            vm,
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = vm.sheet * tabsHeight.toPx() }
                .background(p.background)
                .pointerInput(Unit) { }
                .padding(bottom = bottom)
                .height(64.dp),
        )
    }
}

/** Back closes what is on top, following the finger when the system back gesture is used. */
@Composable
private fun BackHandling(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    val sheetOpen by remember { derivedStateOf { vm.sheet > 0f } }
    val spec = spring<Float>(dampingRatio = 1f, stiffness = 520f, visibilityThreshold = 0.001f)
    PredictiveBackHandler(enabled = vm.nav.canPop && !sheetOpen) { progress ->
        vm.nav.beginBackGesture()
        try {
            progress.collect { event -> vm.nav.progress = 1f - event.progress }
            vm.nav.commitBackGesture()
        } catch (e: CancellationException) {
            vm.nav.cancelBackGesture()
            throw e
        }
    }
    val queueRaised by remember { derivedStateOf { vm.queueFull > 0f } }
    BackHandler(enabled = queueRaised && sheetOpen) {
        scope.launch { animate(vm.queueFull, 0f, animationSpec = spec) { v, _ -> vm.queueFull = v } }
    }
    PredictiveBackHandler(enabled = sheetOpen && !queueRaised) { progress ->
        try {
            progress.collect { event -> vm.sheet = 1f - event.progress * 0.25f }
            scope.launch { animate(vm.sheet, 0f, animationSpec = spec) { v, _ -> vm.sheet = v } }
        } catch (e: CancellationException) {
            scope.launch { animate(vm.sheet, 1f, animationSpec = spec) { v, _ -> vm.sheet = v } }
            throw e
        }
    }
    // From a tab's first screen, back goes home; only from home does it leave the app.
    // Back goes to the screen seen before: the tab came from, else Home, then out of the app.
    BackHandler(enabled = !sheetOpen && !vm.nav.canPop && vm.nav.tab != Tab.Home) { vm.nav.backToPreviousTab() }
}

/** Every tab in the order the user set, hidden ones included. */
fun orderedTabs(order: List<String>): List<Tab> {
    val named = order.mapNotNull { n -> Tab.entries.firstOrNull { it.name == n } }.distinct()
    return named + Tab.entries.filter { it !in named }
}

/** The whole order after the shown tabs were rearranged among themselves; hidden ones keep their places. */
fun withShownOrder(order: List<String>, shown: List<Tab>): List<String> {
    val next = shown.iterator()
    return orderedTabs(order).map { if (it in shown && next.hasNext()) next.next() else it }.map { it.name }
}

/** Navigators that have been sent to their start tab already. */
private val started = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Navigator, Boolean>())

/**
 * The tabs in the bar. A tab hidden in settings stays while it is the open one, so the page is not
 * pulled away from under the finger; it leaves the bar once another tab is chosen.
 */
@Composable
private fun AppViewModel.tabs(): List<Tab> {
    val hidden = prefs.hiddenTabs
    if (started.add(nav) && prefs.startOnLastTab) {
        // Before the pager places its first page, so the app opens there rather than sliding to it.
        val last = Tab.entries.firstOrNull { it.name == prefs.lastTab }
        if (last != null && last != Tab.Home && last.name !in hidden) nav.select(last)
    }
    return orderedTabs(prefs.tabOrder).filter { it == Tab.Home || it == nav.tab || it.name !in hidden }
}

/**
 * Bottom tabs with their names; a pill behind the icon marks the open one. A long press lifts a tab,
 * which then follows the finger sideways and swaps with the ones it passes.
 */
@Composable
private fun TabBar(vm: AppViewModel, modifier: Modifier) {
    val p = LocalPalette.current
    val theme = LocalAppTheme.current
    val tabs = vm.tabs()
    var lifted by remember { mutableStateOf<Tab?>(null) }
    val liftX = remember { mutableFloatStateOf(0f) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // Remembered for "open on the last tab"; the start tab is already chosen by the time this runs.
    LaunchedEffect(vm.nav) {
        androidx.compose.runtime.snapshotFlow { vm.nav.tab }.collect { vm.prefs.lastTab = it.name }
    }
    BoxWithConstraints(modifier) {
        val slot = constraints.maxWidth.toFloat() / tabs.size
        val currentTabs by androidx.compose.runtime.rememberUpdatedState(tabs)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(slot) {
                    // Watches before the tabs do, so a held tab can be lifted even though tabs are tappable.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                                if (!change.pressed) return@withTimeoutOrNull false
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
                            }
                            @Suppress("UNREACHABLE_CODE") false
                        }
                        if (held != null) return@awaitEachGesture
                        val i = (down.position.x / slot).toInt().coerceIn(0, currentTabs.lastIndex)
                        val tab = currentTabs[i]
                        lifted = tab
                        liftX.floatValue = i * slot
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        var last = down.position.x
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            liftX.floatValue = (liftX.floatValue + change.position.x - last).coerceIn(0f, slot * (currentTabs.size - 1))
                            last = change.position.x
                            val target = ((liftX.floatValue + slot / 2) / slot).toInt().coerceIn(0, currentTabs.lastIndex)
                            val from = currentTabs.indexOf(tab)
                            if (target != from) {
                                val list = currentTabs.toMutableList()
                                list.add(target, list.removeAt(from))
                                vm.prefs.updateTabOrder(withShownOrder(vm.prefs.tabOrder, list))
                            }
                        }
                        lifted = null
                    }
                },
        ) {
            tabs.forEachIndexed { index, tab ->
                androidx.compose.runtime.key(tab) {
                    val x by androidx.compose.animation.core.animateFloatAsState(index * slot, spring(dampingRatio = 0.85f, stiffness = 500f), label = "tabx")
                    TabItem(
                        vm, tab, theme, p,
                        Modifier
                            .width(with(LocalDensity.current) { slot.toDp() })
                            .fillMaxHeight()
                            .zIndex(if (lifted == tab) 1f else 0f)
                            .graphicsLayer {
                                translationX = if (lifted == tab) liftX.floatValue else x
                                val s = if (lifted == tab) 1.12f else 1f
                                scaleX = s
                                scaleY = s
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(vm: AppViewModel, tab: Tab, theme: AppTheme, p: Palette, modifier: Modifier) {
    val selected = vm.nav.tab == tab
    val color = tabColor(tab)
    val shown by androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0f, androidx.compose.animation.core.tween(180), label = "tab")
    Column(
        modifier.clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { vm.nav.select(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // A pill behind the icon marks the open tab.
        Box(Modifier.size(width = 56.dp, height = 30.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = shown
                        scaleX = 0.6f + 0.4f * shown
                    }
                    .then(
                        if (theme == AppTheme.Aero) Modifier.block(color, theme, 15.dp)
                        else Modifier.clip(RoundedCornerShape(15.dp)).background(color.copy(alpha = if (p.isDark) 0.24f else 0.18f)),
                    ),
            )
            TabIcon(tab, if (!selected) p.muted else if (theme == AppTheme.Aero) Blocks.on(color) else color, 22.dp)
        }
        if (vm.prefs.tabLabels) Spacer(Modifier.height(3.dp))
        if (vm.prefs.tabLabels) Txt(
            stringResource(
                when (tab) {
                    Tab.Home -> R.string.home
                    Tab.Playlists -> R.string.place_playlists
                    Tab.Artists -> R.string.place_artists
                    Tab.Albums -> R.string.place_albums
                    Tab.Songs -> R.string.place_songs
                },
            ),
            Type.tiny.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(if (selected) 700 else 500)),
            if (selected) p.text else p.muted,
        )
    }
}

private fun tabColor(tab: Tab) = when (tab) {
    Tab.Home -> Color(0xFFFF8A5C)
    Tab.Playlists -> Blocks.Orange
    Tab.Artists -> Color(0xFF8F6DFF)
    Tab.Albums -> Color(0xFFA9D21E)
    Tab.Songs -> Blocks.Pink
}

@Composable
private fun TabIcon(tab: Tab, tint: Color, size: androidx.compose.ui.unit.Dp = 25.dp) {
    when (tab) {
        Tab.Home -> Icon(Icons.Rounded.Home, null, tint = tint, modifier = Modifier.size(size))
        Tab.Playlists -> Glyph(GlyphKind.Playlists, size, tint)
        Tab.Artists -> Glyph(GlyphKind.Artists, size, tint)
        Tab.Albums -> Glyph(GlyphKind.Albums, size, tint)
        Tab.Songs -> Glyph(GlyphKind.Songs, size, tint)
    }
}

// ---- Inner screen: places on the left, the list in the middle, the player on the right

@Composable
private fun WideShell(vm: AppViewModel, library: Library) {
    val holder = rememberSaveableStateHolder()
    val p = LocalPalette.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BackHandler(enabled = vm.nav.canPop) { vm.nav.pop() }
    BackHandler(enabled = !vm.nav.canPop && vm.nav.tab != Tab.Home) { vm.nav.select(Tab.Home) }
    Row(Modifier.fillMaxSize()) {
        Sidebar(vm, library, Modifier.width(158.dp).fillMaxHeight().padding(top = top, bottom = bottom))
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(0.dp))) {
            CompositionLocalProvider(LocalBottomSpace provides bottom) {
                StackHost(vm.nav, holder) { route -> RouteContent(route, vm, library) }
            }
        }
        PlayerPane(vm, Modifier.width(278.dp).background(p.surface))
    }
}

@Composable
private fun Sidebar(vm: AppViewModel, library: Library, modifier: Modifier) {
    val p = LocalPalette.current
    val theme = LocalAppTheme.current
    val tabs = vm.tabs()
    LazyColumn(modifier.background(p.background), contentPadding = PaddingValues(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items(tabs, key = { it.name }) { tab ->
            val selected = vm.nav.tab == tab
            val color = tabColor(tab)
            val on = Blocks.on(color)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .then(if (selected) Modifier.block(color, theme, 12.dp) else Modifier.clip(RoundedCornerShape(12.dp)))
                    .clickable { vm.nav.select(tab) }
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabIcon(tab, if (selected) on else color, 20.dp)
                Spacer(Modifier.width(8.dp))
                Txt(
                    stringResource(
                        when (tab) {
                            Tab.Home -> R.string.home
                            Tab.Playlists -> R.string.place_playlists
                            Tab.Artists -> R.string.place_artists
                            Tab.Albums -> R.string.place_albums
                            Tab.Songs -> R.string.place_songs
                        },
                    ),
                    Type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)),
                    if (selected) on else p.text,
                )
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        items(library.playlists, key = { it.id }) { playlist ->
            val top = vm.nav.stack.last()
            val selected = top is Route.PlaylistRoute && top.id == playlist.id
            val color = Blocks.forIndex(library.playlists.indexOf(playlist))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) p.surfaceHigh else Color.Transparent)
                    .clickable { vm.nav.openInTab(Tab.Playlists, Route.PlaylistRoute(playlist.id)) }
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
                Spacer(Modifier.width(8.dp))
                Txt(playlist.name, Type.small, if (selected) p.text else p.muted)
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Tappable({ vm.nav.push(Route.SettingsRoute) }, Modifier.size(38.dp), bounded = false) {
                Glyph(GlyphKind.Settings, 20.dp, p.muted)
            }
        }
    }
}

/** Opens the system folder picker at Music, and reads the library again with what was chosen. */
@Composable
fun rememberFolderPicker(vm: AppViewModel): androidx.activity.compose.ManagedActivityResultLauncher<android.net.Uri?, android.net.Uri?> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.persistedUriPermissions
                .filter { it.uri != uri }
                .forEach { context.contentResolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            vm.prefs.setTree(uri.toString())
            vm.refreshLibrary(force = true)
        }
    }
}

/**
 * The tabs side by side: a sideways swipe moves to the next one. Choosing a tab below, or a place on
 * Home, slides one page as well, from whichever side it is on, the same move as opening a page; a tab
 * further away is brought next to the current one first, so no list in between gets built.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TabPager(vm: AppViewModel, holder: androidx.compose.runtime.saveable.SaveableStateHolder, modifier: Modifier, content: @Composable (Route) -> Unit) {
    val tabs = vm.tabs()
    val currentTabs by androidx.compose.runtime.rememberUpdatedState(tabs)
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = tabs.indexOf(vm.nav.tab).coerceAtLeast(0)) { tabs.size }
    // Only a finger's swipe changes the tab from here; a tap's run or a reorder of the tabs must not.
    var swiped by remember { mutableStateOf(false) }
    LaunchedEffect(pager) {
        pager.interactionSource.interactions.collect { if (it is androidx.compose.foundation.interaction.DragInteraction.Start) swiped = true }
    }
    LaunchedEffect(vm.nav.tab) {
        swiped = false
        // Wait for a fling still under way instead of dropping the tap.
        androidx.compose.runtime.snapshotFlow { pager.isScrollInProgress }.first { !it }
        val target = currentTabs.indexOf(vm.nav.tab)
        if (target < 0 || (pager.currentPage == target && pager.currentPageOffsetFraction == 0f)) return@LaunchedEffect
        // One quick run across every tab in between, driven as one distance: the pager's own
        // animation would jump over all but the last three pages.
        val pages = kotlin.math.abs(pager.currentPage - target)
        val distance = ((target - pager.currentPage) - pager.currentPageOffsetFraction) * (pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing)
        pager.animateScrollBy(distance, androidx.compose.animation.core.tween(220 + 70 * pages, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        pager.scrollToPage(target)
    }
    LaunchedEffect(pager) {
        androidx.compose.runtime.snapshotFlow { pager.settledPage }.collect { page ->
            if (swiped) {
                swiped = false
                currentTabs.getOrNull(page)?.let { vm.nav.showTab(it) }
            }
        }
    }
    androidx.compose.foundation.pager.HorizontalPager(
        state = pager,
        modifier = modifier.fillMaxSize(),
        // The neighbours stay built, so a swipe or a run does not build a whole list on its first frame.
        beyondViewportPageCount = 1,
        key = { tabs[it].name },
    ) { page ->
        StackHost(vm.nav, holder, tab = tabs[page], content = content)
    }
}
