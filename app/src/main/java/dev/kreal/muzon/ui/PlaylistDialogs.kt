package dev.kreal.muzon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.kreal.muzon.R
import dev.kreal.muzon.data.Track

/** A window with a title, its content and the buttons at the bottom right. */
@Composable
private fun SmallWindow(title: String, onDismiss: () -> Unit, buttons: @Composable () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surface).padding(top = 20.dp, start = 20.dp, end = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Txt(title, Type.section, p.text, Modifier.padding(end = 8.dp), maxLines = 2)
            Box(Modifier.padding(end = 8.dp)) { content() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { buttons() }
        }
    }
}

@Composable
private fun WindowButton(text: String, color: androidx.compose.ui.graphics.Color, enabled: Boolean = true, onClick: () -> Unit) {
    Tappable(if (enabled) onClick else ({}), Modifier.clip(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 9.dp)) {
        Txt(text, Type.bodyBold, if (enabled) color else color.copy(alpha = 0.35f))
    }
}

/** Asks for a playlist name: a new one, or a new name for one. */
@Composable
fun PlaylistNameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val p = LocalPalette.current
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val ok = value.text.isNotBlank()
    SmallWindow(
        title,
        onDismiss,
        buttons = {
            WindowButton(stringResource(R.string.cancel), p.muted, onClick = onDismiss)
            WindowButton(confirm, p.accent, ok) { onConfirm(value.text.trim()) }
        },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.playlist_name), style = Type.body) },
            textStyle = Type.body,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (ok) onConfirm(value.text.trim()) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = p.text,
                unfocusedTextColor = p.text,
                focusedBorderColor = p.accent,
                cursorColor = p.accent,
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

/** Asks before something that cannot be undone. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val p = LocalPalette.current
    SmallWindow(
        title,
        onDismiss,
        buttons = {
            WindowButton(stringResource(R.string.cancel), p.muted, onClick = onDismiss)
            WindowButton(confirm, p.accent, onClick = onConfirm)
        },
    ) {
        Txt(text, Type.body, p.muted, maxLines = 6)
    }
}

/** Picks one of the app's playlists for [tracks], or makes a new one with them. */
@Composable
fun AddToPlaylistDialog(vm: AppViewModel, tracks: List<Track>, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val library = vm.repository.library.value
    val mine = library?.playlists.orEmpty().filter { it.own }
    var naming by remember { mutableStateOf(false) }
    if (naming) {
        PlaylistNameDialog(stringResource(R.string.playlist_new), "", stringResource(R.string.create), { naming = false }) { name ->
            vm.createPlaylist(name, tracks, open = false)
            onDismiss()
        }
        return
    }
    SmallWindow(
        stringResource(R.string.menu_add_playlist),
        onDismiss,
        buttons = { WindowButton(stringResource(R.string.cancel), p.muted, onClick = onDismiss) },
    ) {
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { naming = true }.padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(p.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, null, tint = p.accent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Txt(stringResource(R.string.playlist_new), Type.bodyBold, p.accent)
                }
            }
            items(mine, key = { it.id }) { playlist ->
                val shown = remember(playlist) { library?.tracksOf(playlist).orEmpty() }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        vm.addToPlaylist(playlist.id, tracks)
                        onDismiss()
                    }.padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Mosaic(shown, Modifier.size(44.dp), 10.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(playlist.name, Type.bodyBold, p.text)
                        Txt(pluralStringResource(R.plurals.songs, shown.size, shown.size), Type.small, p.muted)
                    }
                }
            }
        }
    }
}
