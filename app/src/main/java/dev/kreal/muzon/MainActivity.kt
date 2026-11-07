package dev.kreal.muzon

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.kreal.muzon.ui.AppRoot

class MainActivity : ComponentActivity() {
    private val frameRate = FrameRateKeeper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        preferFastestMode()
        setContent { AppRoot() }
        frameRate.attach()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        preferFastestMode() // each screen of a foldable has its own display modes
        frameRate.displayChanged()
    }

    override fun onStart() {
        super.onStart()
        frameRate.start()
    }

    override fun onStop() {
        frameRate.stop()
        super.onStop()
    }

    /** The phone may otherwise hold the screen at 60 Hz for an app it does not know. */
    private fun preferFastestMode() {
        val display = display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        val attributes = window.attributes
        attributes.preferredDisplayModeId = best.modeId
        attributes.preferredRefreshRate = best.refreshRate
        window.attributes = attributes
        if (Build.VERSION.SDK_INT >= 35) {
            // Otherwise the phone drops to 60 Hz as soon as a finger lifts.
            window.isFrameRatePowerSavingsBalanced = false
        }
    }
}
