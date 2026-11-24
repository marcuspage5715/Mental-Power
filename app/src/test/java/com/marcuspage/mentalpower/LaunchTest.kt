package com.marcuspage.mentalpower

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Starts the activity exactly as the launcher does, and draws the first frame. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LaunchTest {

    @Test
    fun theAppOpensAndDrawsItsFirstFrame() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val decor = activity.window.decorView
        assertNotNull(decor)
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, 1920, 1080)
        decor.draw(Canvas(Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)))
    }
}
