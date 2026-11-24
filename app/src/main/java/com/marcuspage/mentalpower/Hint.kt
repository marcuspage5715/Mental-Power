package com.marcuspage.mentalpower

/**
 * The hint: works out how the room can still be finished from wherever the player has got to,
 * and picks one piece of that plan to put on the board.
 *
 * A piece is only ever taken from a plan that fills the whole room, so the board is always left
 * in a state that can still be solved.
 */
object Hint {

    private class Option(val piece: Int, val variant: Int, val ox: Int, val oy: Int, val cells: List<Cell>)

    /** Places one piece, or returns null when the room cannot be finished as it stands. */
    fun place(room: Room): Placement? {
        val empty = ArrayList<Cell>()
        for (y in 0 until room.h) {
            for (x in 0 until room.w) {
                if (room.inShape(x, y) && !room.filled(x, y)) empty.add(Cell(x, y))
            }
        }
        if (empty.isEmpty()) return null

        val tray = room.tray.toList()
        if (tray.isEmpty()) return null

        // every way each piece in the tray can sit in the cells that are still empty
        val byPiece = LinkedHashMap<Int, MutableList<Option>>()
        val byCell = HashMap<Cell, ArrayList<Option>>()
        for (c in empty) byCell[c] = ArrayList()
        for (item in tray) {
            val def = Pieces.of(item.piece)
            val options = ArrayList<Option>()
            for (v in def.variants.indices) {
                val w = def.sizes[v].x
                val h = def.sizes[v].y
                for (ox in -w + 1..room.w) {
                    for (oy in -h + 1..room.h) {
                        val cells = def.variants[v].map { Cell(it.x + ox, it.y + oy) }
                        if (!cells.all { room.inShape(it.x, it.y) && !room.filled(it.x, it.y) }) continue
                        val option = Option(item.piece, v, ox, oy, cells)
                        options.add(option)
                        for (c in cells) byCell[c]!!.add(option)
                    }
                }
            }
            if (options.isNotEmpty()) byPiece[item.piece] = options
        }

        val covered = HashSet<Cell>()
        val used = HashSet<Int>()
        val plan = ArrayList<Option>()
        var steps = 0

        fun search(): Boolean {
            if (covered.size == empty.size) return true
            if (++steps > 400_000) return false
            var options: List<Option>? = null
            for (c in empty) {
                if (c in covered) continue
                val available = byCell[c]!!.filter { it.piece !in used && it.cells.none { cc -> cc in covered } }
                if (available.isEmpty()) return false
                if (options == null || available.size < options!!.size) options = available
                if (options!!.size == 1) break
            }
            for (o in options!!) {
                covered.addAll(o.cells)
                used.add(o.piece)
                plan.add(o)
                if (search()) return true
                plan.removeAt(plan.size - 1)
                used.remove(o.piece)
                covered.removeAll(o.cells)
            }
            return false
        }

        if (!search()) return null

        // the most cramped piece of the plan is the most useful one to show
        var chosen = plan[0]
        var fewest = Int.MAX_VALUE
        for (o in plan) {
            val count = byPiece[o.piece]?.size ?: 0
            if (count < fewest) {
                fewest = count
                chosen = o
            }
        }

        val item = room.tray.firstOrNull { it.piece == chosen.piece } ?: return null
        if (!room.place(chosen.piece, chosen.variant, chosen.ox, chosen.oy)) return null
        room.tray.remove(item)
        return room.placed.last()
    }
}
