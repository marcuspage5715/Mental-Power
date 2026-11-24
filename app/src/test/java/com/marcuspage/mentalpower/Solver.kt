package com.marcuspage.mentalpower

/**
 * Backtracking exact cover used by the tests: fills a room one empty cell at a time, always
 * choosing the cell with the fewest pieces that can still cover it.
 */
object Solver {

    private class Option(val piece: Int, val variant: Int, val ox: Int, val oy: Int, val cells: List<Cell>)

    fun solve(level: Levels.Level): List<Placement>? {
        val room = Room(level)
        val cells = ArrayList<Cell>()
        for (y in 0 until level.h) for (x in 0 until level.w) if (room.inShape(x, y)) cells.add(Cell(x, y))

        val byCell = HashMap<Cell, ArrayList<Option>>()
        for (c in cells) byCell[c] = ArrayList()
        for (piece in level.pieces) {
            val def = Pieces.of(piece)
            for (v in def.variants.indices) {
                val w = def.sizes[v].x
                val h = def.sizes[v].y
                for (ox in -w + 1..level.w) {
                    for (oy in -h + 1..level.h) {
                        val placed = def.variants[v].map { Cell(it.x + ox, it.y + oy) }
                        if (!placed.all { room.inShape(it.x, it.y) }) continue
                        val option = Option(piece, v, ox, oy, placed)
                        for (c in placed) byCell[c]!!.add(option)
                    }
                }
            }
        }

        val covered = HashSet<Cell>()
        val used = HashSet<Int>()
        val out = ArrayList<Placement>()

        fun search(): Boolean {
            if (covered.size == cells.size) return true
            var options: List<Option>? = null
            for (c in cells) {
                if (c in covered) continue
                val available = byCell[c]!!.filter { it.piece !in used && it.cells.none { cc -> cc in covered } }
                if (available.isEmpty()) return false
                if (options == null || available.size < options!!.size) options = available
                if (options!!.size == 1) break
            }
            for (o in options!!) {
                covered.addAll(o.cells)
                used.add(o.piece)
                out.add(Placement(o.piece, o.variant, o.ox, o.oy))
                if (search()) return true
                out.removeAt(out.size - 1)
                used.remove(o.piece)
                covered.removeAll(o.cells)
            }
            return false
        }

        return if (search()) out else null
    }
}
