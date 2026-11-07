package dev.kreal.muzon.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.kreal.muzon.R

/** A launcher icon: an <activity-alias> in the manifest that points at MainActivity with its own adaptive icon. */
enum class AppIcon(val alias: String, val icon: Int, @StringRes val label: Int) {
    Default("LauncherDefault", R.mipmap.ic_launcher, R.string.icon_violet),
    Orange("LauncherOrange", R.mipmap.ic_launcher_orange, R.string.icon_orange),
    Pink("LauncherPink", R.mipmap.ic_launcher_pink, R.string.icon_pink),
    Teal("LauncherTeal", R.mipmap.ic_launcher_teal, R.string.icon_teal),
    Blue("LauncherBlue", R.mipmap.ic_launcher_blue, R.string.icon_blue),
    Black("LauncherBlack", R.mipmap.ic_launcher_black, R.string.icon_black),
    White("LauncherWhite", R.mipmap.ic_launcher_white, R.string.icon_white),
    Sunset("LauncherSunset", R.mipmap.ic_launcher_sunset, R.string.icon_sunset),
    Note("LauncherNote", R.mipmap.ic_launcher_note, R.string.icon_note),
    Wave("LauncherWave", R.mipmap.ic_launcher_wave, R.string.icon_wave);

    // The manifest names are relative to the namespace, which stays dev.kreal.muzon whatever the applicationId.
    fun component(context: Context) = ComponentName(context.packageName, "dev.kreal.muzon.$alias")

    companion object {
        /** The violet alias from before violet became the default: the same look as [Default]. */
        private const val LEGACY_VIOLET = "LauncherViolet"

        private fun legacy(context: Context) = ComponentName(context.packageName, "dev.kreal.muzon.$LEGACY_VIOLET")

        /** The alias the launcher shows now: one switched on here, else the one the manifest enables. */
        fun current(context: Context): AppIcon {
            val pm = context.packageManager
            return entries.firstOrNull {
                pm.getComponentEnabledSetting(it.component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } ?: Default
        }

        /** Switches the new alias on before the old one off, so the app never has no launcher entry. */
        fun select(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                icon.component(context), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP,
            )
            if (pm.getComponentEnabledSetting(legacy(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(legacy(context), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
            entries.filter { it != icon }.forEach {
                val name = it.component(context)
                val state = pm.getComponentEnabledSetting(name)
                // DEFAULT means what the manifest says: on for the default alias, off for the rest.
                val on = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    (it == Default && state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
                if (on) pm.setComponentEnabledSetting(name, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}

/** Draws the icon's background and foreground layers, cropped to the 72 of 108 dp a launcher shows. */
private fun renderIcon(context: Context, res: Int, px: Int): ImageBitmap? {
    val drawable = context.getDrawable(res) as? AdaptiveIconDrawable ?: return null
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val inset = px * 18 / 72
    listOfNotNull(drawable.background, drawable.foreground).forEach {
        it.setBounds(-inset, -inset, px + inset, px + inset)
        it.draw(canvas)
    }
    return bitmap.asImageBitmap()
}

/** Settings block: a wrapping grid of the launcher icons, the chosen one ringed and ticked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppIconSection() {
    val context = LocalContext.current
    val p = LocalPalette.current
    val px = with(LocalDensity.current) { 56.dp.roundToPx() }
    var selected by remember { mutableStateOf(AppIcon.current(context)) }
    val images = remember(px) { AppIcon.entries.associateWith { renderIcon(context, it.icon, px) } }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Txt(stringResource(R.string.s_app_icon), Type.body)
        Txt(stringResource(R.string.s_app_icon_hint), Type.small, p.muted, maxLines = 3)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon.entries.forEach { icon ->
                val isSelected = icon == selected
                val label = stringResource(icon.label)
                Box(
                    Modifier
                        .size(62.dp)
                        .semantics { this.selected = isSelected }
                        .clickable(role = Role.RadioButton, onClickLabel = label) {
                            if (!isSelected) {
                                AppIcon.select(context, icon)
                                selected = icon
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(shape)
                            .then(if (isSelected) Modifier.border(2.5.dp, p.accent, shape) else Modifier.border(1.dp, p.line, shape)),
                    ) {
                        images[icon]?.let { Image(it, label, Modifier.size(56.dp)) }
                    }
                    if (isSelected) {
                        Box(
                            Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(p.accent),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }
    }
}
