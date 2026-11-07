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
private fun Toggle(title: String, value: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row_(title, value, { onChange(!checked) }) {
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = p.accent, checkedThumbColor = Color.White,
                uncheckedTrackColor = p.surfaceHigh, uncheckedThumbColor = p.muted, uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
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
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.setTree(uri.toString())
            vm.refreshLibrary(force = true)
        }
    }
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

        item { Section(stringResource(R.string.s_playback)) }
        item { Toggle(stringResource(R.string.s_skip_silence), null, prefs.skipSilence) { vm.applySkipSilence(it) } }
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
        item { Toggle(stringResource(R.string.s_keep_screen), null, prefs.keepScreenForLyrics) { prefs.keepScreenForLyrics = it } }

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
