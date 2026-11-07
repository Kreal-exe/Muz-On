package dev.kreal.muzon.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class Tab { Home, Playlists, Artists, Albums, Songs }

sealed class Route(val key: String) {
    data class Root(val tab: Tab) : Route("root:$tab")
    data class PlaylistRoute(val id: String) : Route("playlist:$id")
    data class ArtistRoute(val artistKey: String) : Route("artist:$artistKey")
    data class AlbumRoute(val albumKey: String) : Route("album:$albumKey")
    data class GenreRoute(val name: String) : Route("genre:$name")
    data object RecentRoute : Route("recent")
    data object GenresRoute : Route("genres")
    data object HistoryRoute : Route("history")
    data object MostPlayedRoute : Route("mostPlayed")
    data class FolderRoute(val path: String) : Route("folder:$path")
    data object SearchRoute : Route("search")
    data object SettingsRoute : Route("settings")
}

private val pageSpring = spring<Float>(dampingRatio = 1f, stiffness = 420f)

/**
 * Screen stacks, one per tab. The transition is a single number read only in the layer's draw block,
 * so sliding a page never recomposes or lays out anything. The lower page stays composed, which makes
 * going back immediate: its list is already there at its old position.
 */
class Navigator {
    /** Must come from the composition: animations need its frame clock. */
    lateinit var scope: CoroutineScope

    private val stacks = Tab.entries.associateWith { tab -> mutableStateListOf<Route>(Route.Root(tab)) }
    var tab by mutableStateOf(Tab.Home)
        private set
    val stack get() = stacks.getValue(tab)
    val canPop get() = stack.size > 1

    private val progressState = mutableFloatStateOf(1f)
    var progress: Float
        get() = progressState.floatValue
        set(value) { progressState.floatValue = value }

    private var job: Job? = null

    private fun settle() {
        job?.cancel()
        job = null
    }

    fun select(target: Tab) {
        settle()
        progress = 1f
        if (target == tab) {
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        } else {
            tab = target
        }
    }

    fun openInTab(target: Tab, route: Route) {
        settle()
        progress = 1f
        tab = target
        val list = stacks.getValue(target)
        while (list.size > 1) list.removeAt(list.lastIndex)
        list.add(route)
    }

    fun push(route: Route) {
        if (stack.last() == route) return
        settle()
        stack.add(route)
        progress = 0f
        job = scope.launch {
            withFrameNanos { } // let the new page compose first, then move
            animate(0f, 1f, animationSpec = pageSpring) { v, _ -> progress = v }
        }
    }

    fun pop() {
        if (!canPop) return
        settle()
        job = scope.launch {
            animate(progress, 0f, animationSpec = pageSpring) { v, _ -> progress = v }
            finishPop()
        }
    }

    /** Called from the system back gesture, which then drives [progress] with the finger. */
    fun beginBackGesture() = settle()

    fun commitBackGesture() {
        job = scope.launch {
            animate(progress, 0f, animationSpec = pageSpring) { v, _ -> progress = v }
            finishPop()
        }
    }

    fun cancelBackGesture() {
        job = scope.launch { animate(progress, 1f, animationSpec = pageSpring) { v, _ -> progress = v } }
    }

    // The page leaves first; only on the next frame is the stack at rest again. Doing both at once let
    // the leaving page show for a frame at full width before it was gone.
    private suspend fun finishPop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        withFrameNanos { }
        progress = 1f
    }
}

@Composable
fun StackHost(
    nav: Navigator,
    holder: SaveableStateHolder,
    modifier: Modifier = Modifier,
    content: @Composable (Route) -> Unit,
) {
    val background = LocalPalette.current.background
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val stack = nav.stack
        val shown = stack.takeLast(2)
        shown.forEachIndexed { index, route ->
            val isTop = index == shown.lastIndex
            val hasBelow = shown.size == 2
            key(route.key) {
                holder.SaveableStateProvider(route.key) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = nav.progress
                                when {
                                    !hasBelow -> {
                                        translationX = 0f
                                        alpha = 1f
                                    }
                                    isTop -> translationX = (1f - p) * width
                                    else -> {
                                        translationX = -0.25f * p * width
                                        alpha = if (p >= 1f) 0f else 1f
                                    }
                                }
                            }
                            .background(background)
                            .pointerInput(Unit) { },
                    ) { content(route) }
                }
            }
        }
    }
}
