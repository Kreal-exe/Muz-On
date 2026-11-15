package dev.kreal.muzon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.kreal.muzon.R

/** Room the selection bar takes above the height of the tab bar. */
val SELECTION_EXTRA = 44.dp

/**
 * In place of the tabs while songs are picked: how many, all or none, and what to do with them —
 * play, play next, queue, add to a playlist, heart; in a playlist that can be changed also remove,
 * move to another playlist, to the top or the bottom.
 */
@Composable
fun SelectionBar(vm: AppViewModel, modifier: Modifier) {
    val selection = vm.selection ?: return
    val p = LocalPalette.current
    BackHandler { vm.selection = null }
    val picked = selection.tracks()
    val count = picked.size
    val playlist = selection.playlist?.takeIf { it.editable }
    val allLiked = picked.isNotEmpty() && picked.all { vm.repository.library.value?.byId?.get(it.id)?.favorite == true }
    fun done() { vm.selection = null }
    Column(modifier.padding(horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Icons.Rounded.Close, p.text, 22.dp, 44.dp, label = stringResource(R.string.close)) { done() }
            Txt(stringResource(R.string.sel_count, count), Type.bodyBold, p.text, Modifier.weight(1f))
            val all = count == selection.pool.size && count > 0
            Box(
                Modifier.clip(RoundedCornerShape(14.dp)).clickable {
                    selection.ids.clear()
                    if (!all) selection.ids.addAll(selection.pool.map { it.id })
                }.padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Txt(stringResource(if (all) R.string.sel_none else R.string.sel_all), Type.small, p.accent) }
        }
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            val on = count > 0
            Action(Icons.Rounded.PlayArrow, stringResource(R.string.play), on) { vm.connection.play(picked.filter { it.uri != null }); done() }
            Action(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.menu_play_next), on) { vm.connection.playNext(picked.filter { it.uri != null }); done() }
            Action(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.menu_add_playlist), on) { vm.addToPlaylist = picked; done() }
            Action(
                if (allLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                stringResource(if (allLiked) R.string.fav_remove else R.string.fav_add), on,
            ) { vm.setFavorites(picked, !allLiked); done() }
            if (playlist != null) {
                Action(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.remove), on) { vm.removeFromPlaylist(playlist.id, picked.map { it.id }); done() }
            }
            var more by remember { mutableStateOf(false) }
            Box {
                Action(Icons.Rounded.MoreVert, stringResource(R.string.menu_more), on) { more = true }
                DropdownMenu(more, { more = false }) {
                    @Composable
                    fun item(icon: ImageVector, text: Int, action: () -> Unit) = DropdownMenuItem(
                        text = { Text(stringResource(text), style = Type.body) },
                        leadingIcon = { Icon(icon, null) },
                        onClick = { more = false; action(); done() },
                    )
                    item(Icons.AutoMirrored.Rounded.PlaylistAdd, R.string.menu_add_queue) { vm.connection.addToQueue(picked.filter { it.uri != null }) }
                    if (playlist != null) {
                        item(Icons.AutoMirrored.Rounded.DriveFileMove, R.string.move_to_playlist) {
                            vm.movingFrom = playlist.id
                            vm.addToPlaylist = picked
                        }
                        item(Icons.Rounded.VerticalAlignTop, R.string.move_top) { vm.moveInPlaylist(playlist.id, picked.map { it.id }, toTop = true) }
                        item(Icons.Rounded.VerticalAlignBottom, R.string.move_bottom) { vm.moveInPlaylist(playlist.id, picked.map { it.id }, toTop = false) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val tint = if (enabled) p.text else p.muted.copy(alpha = 0.5f)
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp).width(62.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Txt(label, Type.tiny, tint, maxLines = 2)
    }
}
