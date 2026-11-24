package com.marcuspage.mentalpower

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the level table and the rules against the room data: every room has to be a real
 * puzzle that the pieces it hands out can actually fill.
 */
class LevelsTest {

    @Test
    fun thereAreSixtyRooms() {
        assertEquals(60, Levels.LEVELS.size)
        assertEquals(60, Levels.COUNT)
        assertEquals(36, Levels.ORIGINAL_COUNT)
        Levels.LEVELS.forEachIndexed { i, level ->
            assertEquals(i + 1, level.number)
            assertEquals("room ${level.number}", level.number > Levels.ORIGINAL_COUNT, level.extra)
        }
    }

    @Test
    fun everyPieceIsAPentominoExceptTheSquare() {
        assertEquals(13, Levels.PIECE_SHAPES.size)
        Levels.PIECE_SHAPES.forEachIndexed { i, rows ->
            val cells = rows.sumOf { row -> row.count { it == '#' } }
            if (i == 0) assertEquals("piece 1 is the 2x2 square", 4, cells) else assertEquals(5, cells)
        }
    }

    @Test
    fun theThirteenPiecesAreAllDifferent() {
        val canon = Pieces.ALL.map { p -> p.variants.minByOrNull { it.toString() }!!.toString() }
        assertEquals(13, canon.toSet().size)
    }

    @Test
    fun turningAPieceFourTimesReturnsToTheStart() {
        for (p in Pieces.ALL) {
            for (v in p.variants.indices) {
                var cur = v
                repeat(4) { cur = p.rotated(cur) }
                assertEquals("piece ${p.index}", v, cur)
                assertEquals(v, p.flipped(p.flipped(v)))
            }
        }
    }

    @Test
    fun everyRoomCanBeFilledByThePiecesItHandsOut() {
        for (level in Levels.LEVELS) {
            val area = level.rows.map { row -> row.count { it == '#' } }.sum()
            val capacity = level.pieces.map { p -> if (p == 0) 4 else 5 }.sum()
            assertTrue("room ${level.number}: shape area $area, pieces only cover $capacity", capacity >= area)
            assertTrue("room ${level.number}: area $area is not a whole number of pieces",
                area % 5 == 0 || area % 5 == 4)

            val solution = Solver.solve(level)
            assertNotNull("room ${level.number} has no solution", solution)

            // replay the solution through the real rules
            val room = Room(level)
            for (p in solution!!) {
                assertTrue("room ${level.number}: placement rejected", room.place(p.piece, p.variant, p.ox, p.oy))
            }
            assertTrue("room ${level.number} should be solved", room.isSolved())
        }
    }

    @Test
    fun piecesCannotOverlapOrLeaveTheShape() {
        val room = Room(Levels.LEVELS[0]) // 5x3 rectangle, pieces T L Y
        assertTrue(room.place(3, 0, 0, 0))
        // the same cells again must be refused
        assertTrue(!room.place(3, 0, 0, 0))
        // outside the shape
        assertTrue(!room.place(11, 0, 4, 0))
        assertTrue(!room.place(11, 0, -1, 0))
        assertEquals(1, room.placed.size)
        room.lift(room.placed[0])
        assertEquals(0, room.placed.size)
        assertTrue(!room.isSolved())
    }

    @Test
    fun layoutAlwaysFitsTheAvailableArea() {
        val screens = listOf(1920 to 1080, 2340 to 1080, 1280 to 720, 2400 to 1080)
        for (level in Levels.LEVELS) {
            for ((w, h) in screens) {
                val cell = Geo.boardCell(w - 140, h - 460, level.w, level.h, (h * 0.14).toInt())
                assertTrue(cell >= 4)
                assertTrue("room ${level.number} at ${w}x$h", level.w * cell <= w - 140)
                assertTrue("room ${level.number} at ${w}x$h", level.h * cell <= h - 460)
            }
        }
    }

    @Test
    fun passwordsAreTheOnesTheOriginalGameGave() {
        assertEquals(2, Levels.PASSWORD_TO_LEVEL["KING"])
        assertEquals(23, Levels.PASSWORD_TO_LEVEL["WILD"])
        assertEquals(36, Levels.PASSWORD_TO_LEVEL["FINE"])
        assertEquals("ZEAL", Levels.UNLOCK_ALL_PASSWORD)
        assertEquals(59, Levels.PASSWORD_TO_LEVEL.size) // room 1 has no password
        assertEquals(37, Levels.PASSWORD_TO_LEVEL["BOLD"])
        assertEquals(60, Levels.PASSWORD_TO_LEVEL["DRUM"])
    }
}
