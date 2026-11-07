package dev.kreal.muzon

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.kreal.muzon.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        preferFastestMode()
        setContent { AppRoot() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        preferFastestMode() // each screen of a foldable has its own display modes
    }

    /** The phone may otherwise hold the screen at 60 Hz for an app it does not know. */
    private fun preferFastestMode() {
        val display = display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        if (window.attributes.preferredDisplayModeId != best.modeId) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        }
    }
}
