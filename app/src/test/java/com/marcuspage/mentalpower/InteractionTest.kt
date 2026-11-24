package com.marcuspage.mentalpower

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real touch handling of the game view: a drag from the tray onto the board, a tap
 * that turns a piece, and a drag that lands where it cannot go.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class InteractionTest {

    private fun view(width: Int = 1920, height: Int = 1080, level: Int = 1): GameView {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val v = GameView(activity)
        v.open(level)
        v.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        v.layout(0, 0, width, height)
        v.draw(Canvas(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)))
        return v
    }

    private fun touch(v: View, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        v.onTouchEvent(event)
        event.recycle()
    }

    @Test
    fun draggingAPieceFromTheTrayDropsItOnTheBoard() {
        val v = view()
        val geo = v.debugGeometry()
        val room = v.debugRoom()!!
        val start = geo.trayRects[0]

        val grabX = start.left + geo.trayCell / 2f
        val grabY = start.top + geo.trayCell / 2f
        val dropX = geo.boardX + geo.cell * 0.1f
        val dropY = geo.boardY + geo.cell * 0.1f

        touch(v, MotionEvent.ACTION_DOWN, grabX, grabY)
        touch(v, MotionEvent.ACTION_MOVE, dropX, dropY)
        touch(v, MotionEvent.ACTION_UP, dropX, dropY)

        assertEquals(1, room.placed.size)
        val p = room.placed[0]
        assertEquals(0, p.ox)
        assertEquals(0, p.oy)
        assertEquals(3, p.piece) // the T piece room 1 starts with
        assertEquals(room.tray.size, Levels.LEVELS[0].pieces.size - 1)
    }

    @Test
    fun tappingAPieceInTheTrayTurnsIt() {
        val v = view()
        val geo = v.debugGeometry()
        val room = v.debugRoom()!!
        val start = geo.trayRects[0]
        val x = start.left + geo.trayCell / 2f
        val y = start.top + geo.trayCell / 2f

        val before = room.tray[0].variant
        touch(v, MotionEvent.ACTION_DOWN, x, y)
        touch(v, MotionEvent.ACTION_UP, x, y)

        assertNotEquals(before, room.tray[0].variant)
        assertEquals(0, room.placed.size)
    }

    @Test
    fun droppingAPieceWhereItDoesNotFitLeavesItInTheTray() {
        val v = view()
        val geo = v.debugGeometry()
        val room = v.debugRoom()!!
        val start = geo.trayRects[0]

        val grabX = start.left + geo.trayCell / 2f
        val grabY = start.top + geo.trayCell / 2f
        // far left of the board, the piece would hang off the edge
        val dropX = geo.boardX - geo.cell * 2f
        val dropY = geo.boardY - geo.cell * 2f

        touch(v, MotionEvent.ACTION_DOWN, grabX, grabY)
        touch(v, MotionEvent.ACTION_MOVE, dropX, dropY)
        touch(v, MotionEvent.ACTION_UP, dropX, dropY)

        assertEquals(0, room.placed.size)
        assertEquals(Levels.LEVELS[0].pieces.size, room.tray.size)
    }

    @Test
    fun aPlacedPieceCanBeLiftedAgain() {
        val v = view()
        val geo = v.debugGeometry()
        val room = v.debugRoom()!!
        val start = geo.trayRects[0]
        touch(v, MotionEvent.ACTION_DOWN, start.left + geo.trayCell / 2f, start.top + geo.trayCell / 2f)
        touch(v, MotionEvent.ACTION_MOVE, geo.boardX + geo.cell * 0.1f, geo.boardY + geo.cell * 0.1f)
        touch(v, MotionEvent.ACTION_UP, geo.boardX + geo.cell * 0.1f, geo.boardY + geo.cell * 0.1f)
        assertEquals(1, room.placed.size)

        // grab it on the board and drag it to the far side
        val p = room.placed[0]
        // grab the piece at the corner of its first cell so the grid offset is exact
        val fromX = geo.boardX + p.ox * geo.cell + 2f
        val fromY = geo.boardY + p.oy * geo.cell + 2f
        val toX = fromX + geo.cell * 2f
        touch(v, MotionEvent.ACTION_DOWN, fromX, fromY)
        touch(v, MotionEvent.ACTION_MOVE, toX, fromY)
        touch(v, MotionEvent.ACTION_UP, toX, fromY)

        assertTrue(room.placed.size == 1)
        assertEquals(p.ox + 2, room.placed[0].ox)
    }
}
