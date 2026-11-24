package com.marcuspage.mentalpower

/** Backtracking exact cover used by the tests: fills a room one empty cell at a time. */
object Solver {

    fun solve(level: Levels.Level): List<Placement>? {
        val room = Room(level)
        val cells = ArrayList<Cell>()
        for (y in 0 until level.h) for (x in 0 until level.w) if (room.inShape(x, y)) cells.add(Cell(x, y))

        fun search(remaining: List<Int>, placed: MutableList<Placement>): Boolean {
            if (placed.map { p -> Pieces.of(p.piece).variants[p.variant].size }.sum() == cells.size) return true
            if (remaining.isEmpty()) return false

            var target: Cell? = null
            for (c in cells) if (!room.filled(c.x, c.y)) { target = c; break }
            val t = target ?: return true

            for (pi in remaining.indices) {
                val piece = remaining[pi]
                val def = Pieces.of(piece)
                for (v in def.variants.indices) {
                    for (anchor in def.variants[v]) {
                        val ox = t.x - anchor.x
                        val oy = t.y - anchor.y
                        if (!room.canPlace(piece, v, ox, oy)) continue
                        room.place(piece, v, ox, oy)
                        placed.add(room.placed.last())
                        val rest = remaining.toMutableList().also { it.removeAt(pi) }
                        if (search(rest, placed)) return true
                        placed.removeAt(placed.size - 1)
                        room.lift(room.placed.last())
                    }
                }
            }
            return false
        }

        val out = ArrayList<Placement>()
        return if (search(level.pieces.toList(), out)) out else null
    }
}
