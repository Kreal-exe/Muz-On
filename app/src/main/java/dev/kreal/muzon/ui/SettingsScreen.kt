package dev.kreal.muzon.ui

import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.kreal.muzon.BuildConfig
import dev.kreal.muzon.R
import dev.kreal.muzon.data.AppTheme
import dev.kreal.muzon.data.ColorMode
import dev.kreal.muzon.data.Library
import dev.kreal.muzon.data.LibrarySource
import dev.kreal.muzon.data.LyricsSize
import dev.kreal.muzon.data.Place
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val LANGUAGES = listOf(
    "en" to "English", "ru" to "Русский", "uk" to "Українська", "de" to "Deutsch", "fr" to "Français",
    "es" to "Español", "pt-BR" to "Português (Brasil)", "it" to "Italiano", "pl" to "Polski", "tr" to "Türkçe",
    "nl" to "Nederlands", "ja" to "日本語", "ko" to "한국어", "zh-CN" to "简体中文",
)

const val PRIVACY_URL = "https://kreal-exe.github.io/Muz-On/privacy.html"

@Composable
private fun Section(title: String) {
    Txt(title, Type.tile, LocalPalette.current.accent, Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp))
}

@Composable
private fun Row_(title: String, value: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, Type.body, maxLines = 2)
            if (value != null) Txt(value, Type.small, p.muted, maxLines = 4)
        }
        trailing()
    }
}

@Composable
private fun switchColors() = LocalPalette.current.let { p ->
    SwitchDefaults.colors(
        checkedTrackColor = p.accent, checkedThumbColor = Color.White,
        uncheckedTrackColor = p.surfaceHigh, uncheckedThumbColor = p.muted, uncheckedBorderColor = Color.Transparent,
        disabledCheckedTrackColor = p.accent.copy(alpha = 0.45f), disabledCheckedThumbColor = Color.White,
    )
}

@Composable
private fun Toggle(title: String, value: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row_(title, value, { onChange(!checked) }) {
        Switch(checked, onChange, colors = switchColors())
    }
}

/** A tab with arrows that move it among the others and a switch that shows it; Home always shows. */
@Composable
private fun TabSetting(vm: AppViewModel, tab: Tab, index: Int, order: List<Tab>) {
    val p = LocalPalette.current
    val prefs = vm.prefs
    val shown = tab == Tab.Home || tab.name !in prefs.hiddenTabs
    fun move(by: Int) {
        val list = order.toMutableList()
        list.add(index + by, list.removeAt(index))
        prefs.updateTabOrder(list.map { it.name })
    }
    Row_(tabLabel(tab), null, if (tab == Tab.Home) null else { { prefs.setTabHidden(tab.name, shown) } }) {
        val canUp = index > 0
        val canDown = index < order.lastIndex
        IconTap(Icons.Rounded.KeyboardArrowUp, if (canUp) p.text else p.muted.copy(alpha = 0.35f), label = stringResource(R.string.move_up)) { if (canUp) move(-1) }
        IconTap(Icons.Rounded.KeyboardArrowDown, if (canDown) p.text else p.muted.copy(alpha = 0.35f), label = stringResource(R.string.move_down)) { if (canDown) move(1) }
        Spacer(Modifier.width(6.dp))
        Switch(shown, { prefs.setTabHidden(tab.name, !it) }, enabled = tab != Tab.Home, colors = switchColors())
    }
}

@Composable
private fun tabLabel(tab: Tab) = stringResource(
    when (tab) {
        Tab.Home -> R.string.home
        Tab.Playlists -> R.string.place_playlists
        Tab.Artists -> R.string.place_artists
        Tab.Albums -> R.string.place_albums
        Tab.Songs -> R.string.place_songs
    },
)

/** A row that asks before doing what cannot be undone. */
@Composable
private fun ConfirmRow(title: String, value: String?, question: String, confirm: String, action: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    Row_(title, value, { asking = true })
    if (asking) {
        ConfirmDialog(title, question, confirm, { asking = false }) {
            asking = false
            action()
        }
    }
}

/**
 * Removes the pictures OnlineArt found, with the small copies made from them, and forgets which
 * tracks it found nothing for, so each is looked up again. Returns the bytes freed.
 */
private fun clearOnlineCovers(context: android.content.Context): Long {
    val dir = File(context.cacheDir, "art")
    var freed = 0L
    fun drop(f: File) {
        if (!f.exists()) return
        val size = f.length()
        if (f.delete()) freed += size
    }
    dir.listFiles()?.forEach { f ->
        when {
            f.name.endsWith(".web") -> {
                val id = f.name.removeSuffix(".web")
                drop(f)
                drop(File(dir, "${id}_s.jpg"))
                drop(File(dir, "${id}_l.jpg"))
            }
            f.name.endsWith(".noart2") -> drop(f)
        }
    }
    return freed
}

@Composable
private fun <T> Choice(title: String, current: T, options: List<T>, label: @Composable (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row_(title, label(current), { open = true })
        DropdownMenu(open, { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), style = Type.body) },
                    leadingIcon = { if (option == current) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) },
                    onClick = {
                        onPick(option)
                        open = false
                    },
                )
            }
        }
    }
}

/** Three small tiles that show what each theme does, rather than naming it. */
@Composable
private fun ThemePicker(vm: AppViewModel) {
    val p = LocalPalette.current
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppTheme.entries.forEach { theme ->
            val selected = vm.prefs.theme == theme
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) p.surfaceHigh else p.surface)
                    .clickable { vm.prefs.theme = theme }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(52.dp).block(Blocks.Orange, theme, 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalAppTheme provides theme) {
                        Glyph(GlyphKind.Songs, 30.dp, Color.White)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Txt(
                    stringResource(
                        when (theme) {
                            AppTheme.Poster -> R.string.theme_poster
                            AppTheme.Detailed -> R.string.theme_detailed
                            AppTheme.Aero -> R.string.theme_aero
                        },
                    ),
                    Type.small,
                    if (selected) p.text else p.muted,
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, library: Library) {
    val p = LocalPalette.current
    val prefs = vm.prefs
    val context = LocalContext.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val folderPicker = rememberFolderPicker(vm)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = LocalBottomSpace.current + 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTap(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, p.text, 28.dp) { vm.nav.pop() }
                Txt(stringResource(R.string.settings), Type.section)
            }
        }
        item { Section(stringResource(R.string.s_appearance)) }
        item { ThemePicker(vm) }
        item {
            Choice(
                stringResource(R.string.s_mode), prefs.colorMode, ColorMode.entries,
                {
                    stringResource(
                        when (it) {
                            ColorMode.System -> R.string.mode_system
                            ColorMode.Dark -> R.string.mode_dark
                            ColorMode.Black -> R.string.mode_black
                            ColorMode.Light -> R.string.mode_light
                        },
                    )
                },
            ) { prefs.colorMode = it }
        }
        item { Toggle(stringResource(R.string.s_cover_color), stringResource(R.string.s_cover_color_hint), prefs.coverColor) { prefs.coverColor = it } }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Txt(stringResource(R.string.s_accent), Type.body)
                Spacer(Modifier.height(10.dp))
                // Wraps to a second row on a narrow screen.
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Blocks.accents.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(c)
                            .clickable { prefs.accent = i },
                        contentAlignment = Alignment.Center,
                    ) { if (prefs.accent == i) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
                }
            }
        }

        item { AppIconSection() }
        item { Section(stringResource(R.string.s_interface)) }
        item {
            Choice(
                stringResource(R.string.s_start), prefs.startOnLastTab, listOf(false, true),
                { stringResource(if (it) R.string.start_last else R.string.home) },
            ) { prefs.startOnLastTab = it }
        }
        item { Toggle(stringResource(R.string.s_tab_labels), null, prefs.tabLabels) { prefs.tabLabels = it } }
        item {
            Choice(
                stringResource(R.string.s_album_columns), prefs.albumColumns, listOf(0, 2, 3, 4, 5),
                { if (it == 0) stringResource(R.string.columns_auto) else it.toString() },
            ) { prefs.albumColumns = it }
        }
        item { Toggle(stringResource(R.string.s_queue_numbers), null, prefs.queueNumbers) { prefs.queueNumbers = it } }
        item { Toggle(stringResource(R.string.s_swipe_art), null, prefs.swipeArtwork) { prefs.swipeArtwork = it } }
        item { Toggle(stringResource(R.string.s_haptics), null, prefs.haptics) { prefs.haptics = it } }

        item { Section(stringResource(R.string.s_tabs)) }
        val tabOrder = orderedTabs(prefs.tabOrder)
        tabOrder.forEachIndexed { i, tab ->
            item(key = "tab:${tab.name}") {
                Box(Modifier.animateItem()) { TabSetting(vm, tab, i, tabOrder) }
            }
        }

        item { Section(stringResource(R.string.s_home_screen)) }
        Place.entries.forEach { place ->
            item(key = "place:${place.name}") {
                val places = prefs.places
                // At least one place stays, as on Home itself.
                Toggle(place.label(), null, place in places) { on ->
                    if (on) prefs.updatePlaces(places + place) else if (places.size > 1) prefs.updatePlaces(places - place)
                }
            }
        }
        item { Row_(stringResource(R.string.s_home_reset), null, { prefs.resetHome() }) }

        if (Build.VERSION.SDK_INT >= 33) {
            item { Section(stringResource(R.string.s_language)) }
            item {
                val manager = context.getSystemService(LocaleManager::class.java)
                val currentTag = manager.applicationLocales.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
                val systemLabel = stringResource(R.string.lang_system)
                Choice(
                    stringResource(R.string.s_language),
                    currentTag,
                    listOf<String?>(null) + LANGUAGES.map { it.first },
                    { tag -> if (tag == null) systemLabel else LANGUAGES.firstOrNull { it.first == tag }?.second ?: tag },
                ) { tag ->
                    manager.applicationLocales = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
                }
            }
        }

        item { Section(stringResource(R.string.s_library)) }
        item {
            val status = when {
                library.source == LibrarySource.MusicDatabase ->
                    stringResource(R.string.s_status_db) + " · " + pluralStringResource(R.plurals.songs, library.tracks.size, library.tracks.size)
                library.failure != null -> stringResource(R.string.s_status_failed)
                else -> stringResource(R.string.s_status_device) + " · " + pluralStringResource(R.plurals.songs, library.tracks.size, library.tracks.size)
            }
            Row_(stringResource(R.string.s_source), status)
        }
        item {
            val missing = library.tracks.count { it.uri == null }
            if (missing > 0) Row_(stringResource(R.string.s_missing), pluralStringResource(R.plurals.songs, missing, missing))
        }
        item {
            val folder = prefs.treeUri?.let { Uri.parse(it).lastPathSegment?.substringAfterLast(':') }
            Row_(stringResource(R.string.s_folder), folder ?: stringResource(R.string.folder_none), { folderPicker.launch(musicFolderUri()) })
        }
        item { Row_(stringResource(R.string.s_update), null, { vm.refreshLibrary(force = true) }) }
        item {
            val off = stringResource(R.string.sleep_off)
            Choice(stringResource(R.string.s_min_length), prefs.minTrackSeconds, listOf(0, 10, 30, 60), { if (it == 0) off else stringResource(R.string.seconds_whole, it) }) {
                if (it == prefs.minTrackSeconds) return@Choice
                prefs.minTrackSeconds = it
                vm.viewModelScope.launch {
                    vm.repository.reapplyFilters()
                    vm.repository.library.value?.let(vm.connection::onLibrary)
                }
            }
        }

        item { Toggle(stringResource(R.string.s_online_art), stringResource(R.string.s_online_art_hint), prefs.onlineArt) { prefs.onlineArt = it } }

        item { Section(stringResource(R.string.s_playback)) }
        item { Toggle(stringResource(R.string.s_skip_silence), null, prefs.skipSilence) { vm.applySkipSilence(it) } }
        item { Toggle(stringResource(R.string.s_auto_skip), null, prefs.autoSkip) { prefs.autoSkip = it } }
        item { Toggle(stringResource(R.string.s_keep_playing), null, prefs.keepPlayingWhenRemoved) { prefs.keepPlayingWhenRemoved = it } }
        item { Toggle(stringResource(R.string.s_pause_interrupt), stringResource(R.string.s_pause_interrupt_hint), prefs.pauseOnInterrupt) { prefs.pauseOnInterrupt = it } }
        item { Toggle(stringResource(R.string.s_resume_connect), stringResource(R.string.s_resume_connect_hint), prefs.resumeOnConnect) { prefs.resumeOnConnect = it } }
        item {
            Choice(stringResource(R.string.s_recent), prefs.recentDays, listOf(0, 7, 30, 90, 365), { if (it == 0) stringResource(R.string.recent_newest) else stringResource(R.string.recent_days, it) }) {
                prefs.recentDays = it
            }
        }
        item {
            Choice(stringResource(R.string.s_seek_step), prefs.seekStepSeconds, listOf(5, 10, 15, 30), { stringResource(R.string.seconds_whole, it) }) {
                prefs.seekStepSeconds = it
            }
        }
        item {
            val session = (context.applicationContext as dev.kreal.muzon.MuzOnApp).exoPlayer?.audioSessionId
            val intent = Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                .putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                .putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
                .apply { if (session != null) putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, session) }
            // Only offered when the phone has an equaliser to open.
            if (intent.resolveActivity(context.packageManager) != null) {
                Row_(stringResource(R.string.s_equalizer), null, { runCatching { context.startActivity(intent) } })
            }
        }
        item {
            Choice(stringResource(R.string.s_speed), prefs.speed, listOf(0.75f, 1f, 1.25f, 1.5f, 2f), { "×" + (if (it % 1f == 0f) it.toInt().toString() else it.toString()) }) {
                vm.applySpeed(it)
            }
        }
        item {
            val conn = vm.connection
            var chosen by remember { mutableStateOf(0) }
            val off = stringResource(R.string.sleep_off)
            val active = conn.sleepEndsAt > 0
            Choice(
                stringResource(R.string.s_sleep),
                if (active) chosen else 0,
                listOf(0, 15, 30, 45, 60, 90),
                { if (it == 0) off else stringResource(R.string.sleep_min, it) },
            ) {
                chosen = it
                conn.setSleepTimer(it)
            }
        }

        item { Section(stringResource(R.string.lyrics)) }
        item {
            Choice(
                stringResource(R.string.s_lyrics_size), prefs.lyricsSize, LyricsSize.entries,
                {
                    stringResource(
                        when (it) {
                            LyricsSize.Small -> R.string.size_small
                            LyricsSize.Medium -> R.string.size_medium
                            LyricsSize.Large -> R.string.size_large
                        },
                    )
                },
            ) { prefs.lyricsSize = it }
        }
        item {
            Choice(
                stringResource(R.string.s_lyrics_lead),
                prefs.lyricsLeadMs,
                listOf(0, 250, 500, 750, 1000, 1500),
                { stringResource(R.string.seconds_short, it / 1000f) },
            ) { prefs.lyricsLeadMs = it }
        }
        item { Toggle(stringResource(R.string.s_cover_lyrics), null, prefs.coverLyrics) { prefs.coverLyrics = it } }
        item { Toggle(stringResource(R.string.s_keep_screen), null, prefs.keepScreenForLyrics) { prefs.keepScreenForLyrics = it } }
        item { Toggle(stringResource(R.string.s_lyric_popup), null, prefs.lyricPopup) { prefs.lyricPopup = it } }

        item { Section(stringResource(R.string.s_data)) }
        item {
            val title = stringResource(R.string.s_clear_history)
            ConfirmRow(title, null, stringResource(R.string.s_clear_history_confirm), stringResource(R.string.clear)) { prefs.clearHistory() }
        }
        item {
            val title = stringResource(R.string.s_reset_plays)
            val hint = stringResource(R.string.s_reset_plays_hint)
            ConfirmRow(title, hint, hint, stringResource(R.string.reset)) { prefs.resetLocalPlays() }
        }
        item {
            var freed by remember { mutableStateOf<Long?>(null) }
            val title = stringResource(R.string.s_clear_covers)
            val hint = freed?.let { stringResource(R.string.covers_cleared, android.text.format.Formatter.formatShortFileSize(context, it)) }
                ?: stringResource(R.string.s_clear_covers_hint)
            ConfirmRow(title, hint, stringResource(R.string.s_clear_covers_hint), stringResource(R.string.clear)) {
                vm.viewModelScope.launch {
                    freed = withContext(Dispatchers.IO) { clearOnlineCovers(context.applicationContext) }
                    context.imageLoader.memoryCache?.clear()
                }
            }
        }

        item { Section(stringResource(R.string.s_about)) }
        item { Row_(stringResource(R.string.app_name), BuildConfig.VERSION_NAME) }
        item {
            Row_(stringResource(R.string.s_privacy), null, {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
            })
        }
    }
}

fun musicFolderUri(): Uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3AMusic")
