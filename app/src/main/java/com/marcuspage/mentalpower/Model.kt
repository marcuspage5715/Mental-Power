package com.marcuspage.mentalpower

/** A cell on the unit grid. */
data class Cell(val x: Int, val y: Int)

/**
 * One of the 13 pieces of the game, with every distinct orientation worked out once.
 *
 * A piece is described by the cells of its canonical orientation; [variants] holds every
 * rotation and mirror of it, de-duplicated (so the O piece has one variant, the I piece
 * two, and an asymmetric pentomino all eight).
 */
class PieceDef(val index: Int, shapeRows: List<String>) {

    val variants: List<List<Cell>>
    val sizes: List<Cell>

    init {
        val base = ArrayList<Cell>()
        shapeRows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch -> if (ch == '#') base.add(Cell(x, y)) }
        }
        val seen = LinkedHashSet<List<Cell>>()
        var cur: List<Cell> = base
        repeat(4) {
            seen.add(normalise(cur))
            seen.add(normalise(cur.map { c -> Cell(-c.x, c.y) }))
            cur = cur.map { c -> Cell(-c.y, c.x) }
        }
        variants = seen.toList()
        sizes = variants.map { v -> Cell(v.maxOf { it.x } + 1, v.maxOf { it.y } + 1) }
    }

    /** Index of the same piece turned a quarter turn clockwise. */
    fun rotated(variant: Int): Int = indexOf(normalise(variants[variant].map { c -> Cell(-c.y, c.x) }), variant)

    /** Index of the same piece mirrored left to right. */
    fun flipped(variant: Int): Int = indexOf(normalise(variants[variant].map { c -> Cell(-c.x, c.y) }), variant)

    private fun indexOf(shape: List<Cell>, fallback: Int): Int {
        val i = variants.indexOf(shape)
        return if (i >= 0) i else fallback
    }

    companion object {
        fun normalise(cells: List<Cell>): List<Cell> {
            val mx = cells.minOf { it.x }
            val my = cells.minOf { it.y }
            return cells.map { Cell(it.x - mx, it.y - my) }
                .sortedWith(compareBy({ it.y }, { it.x }))
        }
    }
}

/** Every piece of the game, built once from the level table. */
object Pieces {
    val ALL: List<PieceDef> = Levels.PIECE_SHAPES.mapIndexed { i, rows -> PieceDef(i, rows) }
    fun of(index: Int): PieceDef = ALL[index]
}

/** A piece waiting in the tray. */
data class TrayItem(val piece: Int, var variant: Int)

/** A piece sitting on the board, at grid offset ([ox], [oy]). */
data class Placement(val piece: Int, val variant: Int, val ox: Int, val oy: Int)

/** The whole state of a room, kept so a move can be taken back. */
class RoomSnapshot(val placed: List<Placement>, val tray: List<TrayItem>)

/**
 * The live state of one room: which cells have to be filled, which pieces are on the board
 * and which are still in the tray.
 */
class Room(val level: Levels.Level) {

    val w: Int = level.w
    val h: Int = level.h

    /** True for every cell of the shape that must end up covered. */
    val target = BooleanArray(w * h)

    /** Index into [placed] for each cell, or -1 when the cell is empty. */
    private val owner = IntArray(w * h) { -1 }

    val placed = ArrayList<Placement>()
    val tray = ArrayList<TrayItem>()

    init {
        level.rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch -> if (ch == '#') target[y * w + x] = true }
        }
        level.pieces.forEach { tray.add(TrayItem(it, 0)) }
    }

    fun inShape(x: Int, y: Int): Boolean =
        x >= 0 && y >= 0 && x < w && y < h && target[y * w + x]

    fun filled(x: Int, y: Int): Boolean =
        x >= 0 && y >= 0 && x < w && y < h && owner[y * w + x] >= 0

    fun cellsOf(piece: Int, variant: Int, ox: Int, oy: Int): List<Cell> =
        Pieces.of(piece).variants[variant].map { Cell(it.x + ox, it.y + oy) }

    fun canPlace(piece: Int, variant: Int, ox: Int, oy: Int): Boolean {
        for (c in cellsOf(piece, variant, ox, oy)) {
            if (!inShape(c.x, c.y)) return false
            if (owner[c.y * w + c.x] >= 0) return false
        }
        return true
    }

    fun place(piece: Int, variant: Int, ox: Int, oy: Int): Boolean {
        if (!canPlace(piece, variant, ox, oy)) return false
        val idx = placed.size
        placed.add(Placement(piece, variant, ox, oy))
        for (c in cellsOf(piece, variant, ox, oy)) owner[c.y * w + c.x] = idx
        return true
    }

    fun placementAt(x: Int, y: Int): Placement? {
        if (x < 0 || y < 0 || x >= w || y >= h) return null
        val o = owner[y * w + x]
        return if (o < 0) null else placed[o]
    }

    /** Takes [p] off the board. */
    fun lift(p: Placement) {
        val idx = placed.indexOf(p)
        if (idx < 0) return
        for (c in cellsOf(p.piece, p.variant, p.ox, p.oy)) {
            if (c.x in 0 until w && c.y in 0 until h) owner[c.y * w + c.x] = -1
        }
        placed.removeAt(idx)
        for (i in owner.indices) if (owner[i] > idx) owner[i]--
    }

    fun isSolved(): Boolean {
        for (i in target.indices) if (target[i] && owner[i] < 0) return false
        return true
    }

    /** The room as it stands, to be handed back to [restore] later. */
    fun snapshot(): RoomSnapshot =
        RoomSnapshot(placed.toList(), tray.map { TrayItem(it.piece, it.variant) })

    /** Puts the room back exactly as it was when [snapshot] was taken. */
    fun restore(snapshot: RoomSnapshot) {
        for (i in owner.indices) owner[i] = -1
        placed.clear()
        for (p in snapshot.placed) {
            val idx = placed.size
            placed.add(p)
            for (c in cellsOf(p.piece, p.variant, p.ox, p.oy)) {
                if (c.x in 0 until w && c.y in 0 until h) owner[c.y * w + c.x] = idx
            }
        }
        tray.clear()
        for (item in snapshot.tray) tray.add(TrayItem(item.piece, item.variant))
    }

    /** Keeps the tray in the order the room handed its pieces out. */
    fun sortTray() {
        tray.sortBy { item -> level.pieces.indexOf(item.piece) }
    }

    fun reset() {
        for (i in owner.indices) owner[i] = -1
        placed.clear()
        tray.clear()
        level.pieces.forEach { tray.add(TrayItem(it, 0)) }
    }
}

/** Geometry helpers, kept free of Android so they can be unit tested. */
object Geo {

    /** Side of a board cell: the biggest square that fits [lw] x [lh] cells in the area. */
    fun boardCell(availW: Int, availH: Int, lw: Int, lh: Int, maxCell: Int): Int {
        val fit = minOf(availW / lw.coerceAtLeast(1), availH / lh.coerceAtLeast(1))
        return maxOf(minOf(fit, maxCell), 4)
    }

    /** Side of a tray cell, so that [units] cells of tray fit across [availW] and [availH]. */
    fun trayCell(availW: Int, availH: Int, units: Double): Int {
        val byWidth = (availW / units.coerceAtLeast(0.1)).toInt()
        val byHeight = (availH * 0.60 / 3.0).toInt()
        val tallPiece = (availH / 5.6).toInt()
        return maxOf(minOf(byWidth, byHeight, tallPiece), 3)
    }
}
