package com.marcuspage.mentalpower

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws the real view off screen and writes a PNG of each screen, so the layout and the
 * palette can be looked at without a phone. Only runs when asked for, with -Dshot.dir=...
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTest {

    private val outDir = System.getProperty("shot.dir") ?: ""

    private fun shoot(name: String, width: Int, height: Int, setup: (GameView) -> Unit) {
        if (outDir.isEmpty()) return
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        val view = GameView(activity)
        setup(view)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File(outDir)
        dir.mkdirs()
        File(dir, "$name-${width}x$height.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun renderScreens() {
        if (outDir.isEmpty()) return
        for ((w, h) in listOf(1920 to 1080, 2340 to 1080, 1280 to 720)) {
            shoot("title", w, h) { }
            shoot("rooms", w, h) { it.showRooms() }
            shoot("password", w, h) { it.showPassword() }
            shoot("room01", w, h) { it.open(1) }
            shoot("room17", w, h) { it.open(17) }
            shoot("room25", w, h) { it.open(25) }
            shoot("room32", w, h) { it.open(32) }
            shoot("room36", w, h) { it.open(36) }
            shoot("clear", w, h) { it.open(12); it.debugClear() }
        }
    }
}
