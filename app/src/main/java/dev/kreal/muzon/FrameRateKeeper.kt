package dev.kreal.muzon

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Some phones ignore a window's preferred rate and fall to 60 Hz when a finger lifts. An explicit
 * vote on a surface is the one signal they respect, and it counts only while that surface receives
 * frames, so a single pixel in the corner gets one twice a second while the app is on screen.
 */
class FrameRateKeeper(private val activity: Activity) : SurfaceHolder.Callback {
    private val handler = Handler(Looper.getMainLooper())
    private var surface: Surface? = null
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            val current = surface ?: return
            runCatching {
                current.lockCanvas(null).also { it.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR) }
                    .let(current::unlockCanvasAndPost)
            }
            if (running) handler.postDelayed(this, 500)
        }
    }

    fun attach() {
        if (Build.VERSION.SDK_INT < 31) return
        val view = SurfaceView(activity)
        view.holder.setFormat(android.graphics.PixelFormat.TRANSPARENT)
        view.holder.addCallback(this)
        activity.addContentView(view, FrameLayout.LayoutParams(1, 1))
    }

    fun start() {
        running = true
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun vote() {
        if (Build.VERSION.SDK_INT < 31) return
        val rate = activity.display?.mode?.let { mode ->
            activity.display?.supportedModes
                ?.filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
                ?.maxOfOrNull { it.refreshRate }
        } ?: return
        surface?.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface.CHANGE_FRAME_RATE_ALWAYS)
    }

    fun displayChanged() = vote()

    override fun surfaceCreated(holder: SurfaceHolder) {
        surface = holder.surface
        vote()
        if (running) start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surface = null
        handler.removeCallbacks(tick)
    }
}
