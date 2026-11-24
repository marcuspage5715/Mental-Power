package com.marcuspage.mentalpower

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hint has to put exactly one piece on the board, and never leave a room unwinnable. */
class HintTest {

    @Test
    fun aHintPlacesExactlyOnePieceFromTheTray() {
        val room = Room(Levels.LEVELS[0])
        val before = room.tray.size
        val placed = Hint.place(room)
        assertNotNull("the first room should have a hint", placed)
        assertEquals(1, room.placed.size)
        assertEquals(before - 1, room.tray.size)
        assertTrue("the placed piece must have come out of the tray",
            room.tray.none { it.piece == placed!!.piece })
        assertTrue("the placed piece must be on the board", room.filled(placed!!.ox, placed.oy))
    }

    @Test
    fun hintsAloneCanFinishEveryRoom() {
        for (level in Levels.LEVELS) {
            val room = Room(level)
            var guard = 0
            while (!room.isSolved()) {
                val placed = Hint.place(room)
                assertNotNull("room ${level.number}: the hint ran out after $guard pieces", placed)
                if (++guard > 20) break
            }
            assertTrue("room ${level.number}: hints did not finish the room", room.isSolved())
            assertEquals("room ${level.number}: the tray does not match the board",
                level.pieces.size - room.placed.size, room.tray.size)
        }
    }

    @Test
    fun aFinishedRoomHasNoHintLeftToGive() {
        val room = Room(Levels.LEVELS[0])
        while (!room.isSolved()) {
            if (Hint.place(room) == null) break
        }
        assertTrue(room.isSolved())
        assertNull(Hint.place(room))
    }
}
