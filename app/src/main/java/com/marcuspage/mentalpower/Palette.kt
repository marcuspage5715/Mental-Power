package com.marcuspage.mentalpower

/** The soft, light palette the whole app is drawn with. */
object Palette {

    val BG = 0xFFF7F3ED.toInt()
    val BG_DEEP = 0xFFEFE8DE.toInt()
    val PANEL = 0xFFFFFFFF.toInt()
    val INK = 0xFF4A4741.toInt()
    val INK_SOFT = 0xFF928C82.toInt()
    val INK_FAINT = 0xFFC6BFB4.toInt()

    val CELL = 0xFFEDE7DE.toInt()
    val CELL_LINE = 0xFFDDD5C9.toInt()
    val CELL_SHADE = 0xFFE2DACD.toInt()

    val ACCENT = 0xFF7E9AB8.toInt()
    val ACCENT_SOFT = 0xFFC9D8E6.toInt()
    val GOOD = 0xFF8FBF9F.toInt()
    val BAD = 0xFFE0A0A0.toInt()

    /** One muted colour per piece, in the order of [Levels.PIECE_SHAPES]. */
    val PIECE = intArrayOf(
        0xFFF2B5A7.toInt(), // O  salmon
        0xFFF7D9A0.toInt(), // V  apricot
        0xFFE9DFA4.toInt(), // Z  sand
        0xFFBFD8B8.toInt(), // T  sage
        0xFFA8D5CF.toInt(), // F  aqua
        0xFFB4C7E7.toInt(), // I  periwinkle
        0xFFC9B8DD.toInt(), // N  lavender
        0xFFF3C4D8.toInt(), // W  rose
        0xFFD6C8A8.toInt(), // U  taupe
        0xFFA9C8B0.toInt(), // P  mint
        0xFFE8B4C8.toInt(), // X  pink
        0xFFC7D2E0.toInt(), // L  mist
        0xFFE5C9A8.toInt(), // Y  peach
    )

    /** A slightly darker tone of [color], for the edge of a tile. */
    fun edge(color: Int): Int = blend(color, 0xFF000000.toInt(), 0.18f)

    /** A slightly lighter tone of [color], for the top of a tile. */
    fun light(color: Int): Int = blend(color, 0xFFFFFFFF.toInt(), 0.45f)

    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    fun blend(a: Int, b: Int, t: Float): Int {
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        val r = (ar + (br - ar) * t).toInt()
        val g = (ag + (bg - ag) * t).toInt()
        val bl = (ab + (bb - ab) * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }
}
