package com.marcuspage.mentalpower

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Everything the player sees and touches: the title screen, the password screen, the room
 * select and the puzzle itself. Drawn with a plain Canvas so the app needs no UI toolkit.
 */
class GameView(context: Context) : View(context) {

    private enum class Screen { TITLE, PASSWORD, ROOMS, PLAY }

    private class Hit(val r: RectF, val id: String)

    private class DragState(
        val item: TrayItem?,
        val original: Placement?,
        val grabX: Int,
        val grabY: Int,
        var fx: Float,
        var fy: Float,
        val downX: Float,
        val downY: Float,
        var variant: Int,
        var moved: Boolean,
    ) {
        val piece: Int get() = item?.piece ?: original!!.piece
    }

    // ---------------------------------------------------------------- state

    private val prefs = context.getSharedPreferences("mental_power", Context.MODE_PRIVATE)

    private var screen = Screen.TITLE
    private var unlocked = 1
    private var room: Room? = null
    private var roomLevel = 1
    private var solved = false

    /** Rooms whose one hint has already been spent. */
    private val hintsUsed = HashSet<Int>()

    private var activeTray: TrayItem? = null
    private var activeBoard: Placement? = null
    private var drag: DragState? = null

    private val password = StringBuilder()
    private var passwordError = false

    private var banner = ""
    private var bannerUntil = 0L

    // ---------------------------------------------------------------- paints

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)

    private val typeRegular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val typeMedium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private val hits = ArrayList<Hit>()
    private val trayRects = ArrayList<RectF>()

    private var vw = 0f
    private var vh = 0f
    private var boardX = 0f
    private var boardY = 0f
    private var cellPx = 0
    private var trayCellPx = 0
    private var trayTop = 0f
    private var trayHeight = 0f

    private val slop = 14f * resources.displayMetrics.density

    private var tone: ToneGenerator? = null
    private var toneTried = false

    init {
        unlocked = prefs.getInt("unlocked", 1).coerceIn(1, Levels.COUNT)
        prefs.getString("hints", "").orEmpty().split(',').forEach { saved ->
            saved.trim().toIntOrNull()?.let { hintsUsed.add(it) }
        }
    }

    private fun saveHints() {
        prefs.edit().putString("hints", hintsUsed.joinToString(",")).apply()
    }

    private fun hintAvailable(level: Int): Boolean = level !in hintsUsed

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        vw = width.toFloat()
        vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return

        hits.clear()
        fillPaint.color = Palette.BG
        canvas.drawRect(0f, 0f, vw, vh, fillPaint)

        when (screen) {
            Screen.TITLE -> drawTitle(canvas)
            Screen.PASSWORD -> drawPassword(canvas)
            Screen.ROOMS -> drawRooms(canvas)
            Screen.PLAY -> drawPlay(canvas)
        }

        if (banner.isNotEmpty() && SystemClock.elapsedRealtime() < bannerUntil) {
            drawBanner(canvas)
            postInvalidateDelayed(60)
        }


    }

    private fun text(
        c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, medium: Boolean,
        align: Paint.Align = Paint.Align.CENTER,
    ) {
        tp.color = color
        tp.textSize = size
        tp.textAlign = align
        tp.typeface = if (medium) typeMedium else typeRegular
        val fm = tp.fontMetrics
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, tp)
    }

    private fun pill(c: Canvas, r: RectF, label: String, filled: Boolean, enabled: Boolean = true) {
        val radius = min(r.height(), r.width()) / 2f
        if (filled) {
            fillPaint.color = if (enabled) Palette.ACCENT else Palette.INK_FAINT
            c.drawRoundRect(r, radius, radius, fillPaint)
            text(c, label, r.centerX(), r.centerY(), r.height() * 0.40f, 0xFFFFFFFF.toInt(), true)
        } else {
            fillPaint.color = Palette.PANEL
            c.drawRoundRect(r, radius, radius, fillPaint)
            strokePaint.color = if (enabled) Palette.ACCENT_SOFT else Palette.INK_FAINT
            strokePaint.strokeWidth = max(2f, r.height() * 0.045f)
            c.drawRoundRect(r, radius, radius, strokePaint)
            text(c, label, r.centerX(), r.centerY(), r.height() * 0.40f, if (enabled) Palette.INK else Palette.INK_FAINT, true)
        }
    }

    private fun button(c: Canvas, r: RectF, label: String, id: String, filled: Boolean, enabled: Boolean = true) {
        pill(c, r, label, filled, enabled)
        if (enabled) hits.add(Hit(RectF(r), id))
    }

    private fun tile(c: Canvas, left: Float, top: Float, size: Float, color: Int, alpha: Float) {
        val inset = size * 0.055f
        val r = RectF(left + inset, top + inset, left + size - inset, top + size - inset)
        val radius = size * 0.22f
        fillPaint.color = Palette.withAlpha(color, alpha)
        c.drawRoundRect(r, radius, radius, fillPaint)
        strokePaint.color = Palette.withAlpha(Palette.edge(color), alpha * 0.5f)
        strokePaint.strokeWidth = max(1f, size * 0.045f)
        c.drawRoundRect(r, radius, radius, strokePaint)
    }

    private fun emptyCell(c: Canvas, left: Float, top: Float, size: Float) {
        val inset = size * 0.055f
        val r = RectF(left + inset, top + inset, left + size - inset, top + size - inset)
        fillPaint.color = Palette.CELL
        c.drawRoundRect(r, size * 0.18f, size * 0.18f, fillPaint)
        strokePaint.color = Palette.CELL_LINE
        strokePaint.strokeWidth = max(1f, size * 0.03f)
        c.drawRoundRect(r, size * 0.18f, size * 0.18f, strokePaint)
    }

    private fun drawPiece(c: Canvas, piece: Int, variant: Int, px: Float, py: Float, cell: Float, alpha: Float) {
        val color = Palette.PIECE[piece % Palette.PIECE.size]
        for (c0 in Pieces.of(piece).variants[variant]) {
            tile(c, px + c0.x * cell, py + c0.y * cell, cell, color, alpha)
        }
    }

    private fun drawBanner(c: Canvas) {
        val size = min(vh * 0.040f, vw * 0.026f)
        tp.textSize = size
        tp.typeface = typeMedium
        val textW = tp.measureText(banner)
        val h = size * 2.4f
        val w = min(vw * 0.94f, textW + size * 3f)
        val r = RectF((vw - w) / 2f, vh * 0.02f, (vw + w) / 2f, vh * 0.02f + h)
        fillPaint.color = Palette.withAlpha(0xFF4A4741.toInt(), 0.92f)
        c.drawRoundRect(r, h / 2f, h / 2f, fillPaint)
        text(c, banner, r.centerX(), r.centerY(), size, 0xFFFFFFFF.toInt(), true)
    }

    // ---------------------------------------------------------------- title

    private fun drawTitle(c: Canvas) {
        val titleSize = min(vw * 0.085f, vh * 0.17f)
        text(c, "MENTAL", vw / 2f, vh * 0.26f, titleSize, Palette.INK, true)
        text(c, "POWER", vw / 2f, vh * 0.26f + titleSize * 1.02f, titleSize, Palette.ACCENT, true)
        text(c, "sixty pentomino rooms", vw / 2f, vh * 0.62f, min(vw * 0.022f, vh * 0.042f), Palette.INK_SOFT, false)

        val bw = min(vw * 0.185f, vh * 0.40f)
        val bh = vh * 0.115f
        val gap = vw * 0.018f
        val total = bw * 4 + gap * 3
        var x = (vw - total) / 2f
        val y = vh * 0.72f

        button(c, RectF(x, y, x + bw, y + bh), "PLAY", "play", true)
        x += bw + gap
        button(c, RectF(x, y, x + bw, y + bh), "ROOMS", "rooms", false)
        x += bw + gap
        button(c, RectF(x, y, x + bw, y + bh), "PASSWORD", "password", false)
        x += bw + gap
        button(c, RectF(x, y, x + bw, y + bh), "EXIT", "exit", false)

        val startAt = if (unlocked > 1) "room $unlocked of ${Levels.COUNT}" else "from the first room"
        text(c, startAt, vw / 2f, y + bh + vh * 0.075f, min(vw * 0.02f, vh * 0.036f), Palette.INK_SOFT, false)
        val hintsLeft = Levels.COUNT - hintsUsed.size
        text(c, "36 rooms of the original game, 24 new ones  ·  $hintsLeft hints left",
            vw / 2f, y + bh + vh * 0.145f, min(vw * 0.018f, vh * 0.032f), Palette.INK_FAINT, false)

        tp.textAlign = Paint.Align.LEFT
        tp.color = Palette.INK_FAINT
        tp.textSize = min(vw * 0.016f, vh * 0.028f)
        tp.typeface = typeRegular
        c.drawText(BUILD_STAMP, vw * 0.02f, vh - vh * 0.025f, tp)
    }

    // ---------------------------------------------------------------- password

    private fun drawPassword(c: Canvas) {
        text(c, "PASSWORD", vw / 2f, vh * 0.13f, min(vw * 0.038f, vh * 0.085f), Palette.INK, true)

        val slotW = vh * 0.14f
        val slotH = vh * 0.17f
        val gap = vw * 0.014f
        val totalW = slotW * 4 + gap * 3
        var sx = (vw - totalW) / 2f
        val sy = vh * 0.24f
        for (i in 0 until 4) {
            val r = RectF(sx, sy, sx + slotW, sy + slotH)
            fillPaint.color = Palette.PANEL
            c.drawRoundRect(r, slotH * 0.2f, slotH * 0.2f, fillPaint)
            strokePaint.color = if (passwordError) Palette.BAD else Palette.CELL_LINE
            strokePaint.strokeWidth = max(2f, vh * 0.006f)
            c.drawRoundRect(r, slotH * 0.2f, slotH * 0.2f, strokePaint)
            val ch = if (i < password.length) password[i].toString() else ""
            text(c, ch, r.centerX(), r.centerY(), slotH * 0.55f, Palette.INK, true)
            sx += slotW + gap
        }

        val cols = 7
        val rows = 4
        val keys = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val gridTop = vh * 0.50f
        val gridBottom = vh * 0.90f
        val kw = (vw * 0.72f) / cols
        val kh = (gridBottom - gridTop) / rows
        val gridLeft = (vw - kw * cols) / 2f
        for (i in 0 until cols * rows) {
            val r = RectF(
                gridLeft + (i % cols) * kw + kw * 0.06f,
                gridTop + (i / cols) * kh + kh * 0.08f,
                gridLeft + (i % cols + 1) * kw - kw * 0.06f,
                gridTop + (i / cols + 1) * kh - kh * 0.08f,
            )
            if (i < keys.length) {
                button(c, r, keys[i].toString(), "key:${keys[i]}", false)
            } else if (i == keys.length) {
                button(c, r, "DEL", "del", false)
            } else if (i == keys.length + 1) {
                button(c, r, "OK", "ok", true)
            }
        }

        button(c, RectF(vw * 0.02f, vh * 0.06f, vw * 0.02f + vw * 0.12f, vh * 0.06f + vh * 0.085f), "BACK", "back", false)
    }

    // ---------------------------------------------------------------- room select

    private fun drawRooms(c: Canvas) {
        text(c, "ROOMS", vw / 2f, vh * 0.14f, min(vw * 0.04f, vh * 0.09f), Palette.INK, true)

        val cols = 10
        val rows = 6
        val areaW = vw * 0.74f
        val areaH = vh * 0.56f
        val cw = areaW / cols
        val ch = areaH / rows
        val left = (vw - areaW) / 2f
        val top = vh * 0.26f
        for (i in 0 until cols * rows) {
            val n = i + 1
            val r = RectF(
                left + (i % cols) * cw + cw * 0.10f,
                top + (i / cols) * ch + ch * 0.10f,
                left + (i % cols + 1) * cw - cw * 0.10f,
                top + (i / cols + 1) * ch - ch * 0.10f,
            )
            val open = n <= unlocked
            if (open) {
                if (n > Levels.ORIGINAL_COUNT) {
                    fillPaint.color = Palette.withAlpha(Palette.ACCENT_SOFT, 0.55f)
                    c.drawRoundRect(r, ch * 0.2f, ch * 0.2f, fillPaint)
                }
                button(c, r, n.toString(), "room:$n", n == unlocked && unlocked > 1)
            } else {
                fillPaint.color = Palette.BG_DEEP
                c.drawRoundRect(r, ch * 0.2f, ch * 0.2f, fillPaint)
                text(c, n.toString(), r.centerX(), r.centerY(), r.height() * 0.38f, Palette.INK_FAINT, true)
            }
        }

        button(c, RectF(vw * 0.02f, vh * 0.06f, vw * 0.02f + vw * 0.12f, vh * 0.06f + vh * 0.085f), "BACK", "back", false)
        text(c, "1-36 the original game   37-60 new", vw / 2f, vh * 0.94f,
            min(vw * 0.016f, vh * 0.028f), Palette.INK_FAINT, false)
    }

    // ---------------------------------------------------------------- the room

    private fun drawPlay(c: Canvas) {
        val r = room ?: return

        val hudH = vh * 0.10f
        trayHeight = vh * 0.30f
        trayTop = vh - trayHeight
        val margin = min(vw, vh) * 0.04f

        // --- hud
        text(c, "ROOM $roomLevel", margin, hudH * 0.52f, hudH * 0.40f, Palette.INK, true, Paint.Align.LEFT)

        val bh = hudH * 0.62f
        val by = (hudH - bh) / 2f
        var bx = vw - margin
        val menuW = bh * 1.6f
        button(c, RectF(bx - menuW, by, bx, by + bh), "MENU", "menu", false)
        bx -= menuW + vw * 0.008f
        val resetW = bh * 1.7f
        button(c, RectF(bx - resetW, by, bx, by + bh), "RESET", "reset", false)
        bx -= resetW + vw * 0.008f
        val flipW = bh * 1.5f
        button(c, RectF(bx - flipW, by, bx, by + bh), "FLIP", "flip", false)
        bx -= flipW + vw * 0.008f
        val rotW = bh * 1.9f
        button(c, RectF(bx - rotW, by, bx, by + bh), "ROTATE", "rotate", false)
        bx -= rotW + vw * 0.008f
        val hintW = bh * 1.55f
        button(c, RectF(bx - hintW, by, bx, by + bh), "HINT", "hint", false, hintAvailable(roomLevel))

        // --- board
        val availW = vw - margin * 2f
        val availH = trayTop - hudH - margin
        // the panel around the shape needs 0.45 of a cell on every side, so the cells have to
        // be shrunk until shape plus panel fits the area between the hud and the tray
        var cellTry = Geo.boardCell(availW.toInt(), availH.toInt(), r.w, r.h, (vh * 0.14f).toInt())
        while (cellTry > 8 && (r.w * cellTry + cellTry * 0.9f > availW || r.h * cellTry + cellTry * 0.9f > availH)) cellTry -= 2
        cellPx = cellTry
        val bw = r.w * cellPx
        val bh2 = r.h * cellPx
        boardX = (vw - bw) / 2f
        boardY = hudH + margin * 0.4f + (availH - bh2) / 2f

        val pad = cellPx * 0.45f
        fillPaint.color = Palette.PANEL
        val panel = RectF(boardX - pad, boardY - pad, boardX + bw + pad, boardY + bh2 + pad)
        c.drawRoundRect(panel, cellPx * 0.5f, cellPx * 0.5f, fillPaint)
        strokePaint.color = Palette.CELL_LINE
        strokePaint.strokeWidth = max(1f, cellPx * 0.05f)
        c.drawRoundRect(panel, cellPx * 0.5f, cellPx * 0.5f, strokePaint)

        val cell = cellPx.toFloat()
        for (y in 0 until r.h) {
            for (x in 0 until r.w) {
                if (r.target[y * r.w + x]) emptyCell(c, boardX + x * cell, boardY + y * cell, cell)
            }
        }
        for (p in r.placed) {
            drawPiece(c, p.piece, p.variant, boardX + p.ox * cell, boardY + p.oy * cell, cell, 1f)
        }

        // --- drop preview
        val d = drag
        if (d != null) {
            val (fox, foy) = dragOrigin(d)
            val ox = fox.roundToInt()
            val oy = foy.roundToInt()
            val ok = r.canPlace(d.piece, d.variant, ox, oy)
            val tint = if (ok) Palette.GOOD else Palette.BAD
            for (c0 in Pieces.of(d.piece).variants[d.variant]) {
                val gx = boardX + (ox + c0.x) * cell
                val gy = boardY + (oy + c0.y) * cell
                fillPaint.color = Palette.withAlpha(tint, 0.45f)
                c.drawRoundRect(RectF(gx + cell * 0.06f, gy + cell * 0.06f, gx + cell * 0.94f, gy + cell * 0.94f),
                    cell * 0.2f, cell * 0.2f, fillPaint)
            }
            drawPiece(c, d.piece, d.variant, boardX + ox * cell, boardY + oy * cell, cell, 0.92f)
        }

        // --- tray
        fillPaint.color = Palette.BG_DEEP
        c.drawRect(0f, trayTop, vw, vh, fillPaint)
        layoutTray(r)
        val tcell = trayCellPx.toFloat()
        for (i in r.tray.indices) {
            val item = r.tray[i]
            if (d != null && d.item === item) continue
            val rect = trayRects[i]
            drawPiece(c, item.piece, item.variant, rect.left, rect.top, tcell, 1f)
            if (activeTray === item && activeBoard == null) {
                strokePaint.color = Palette.withAlpha(Palette.ACCENT, 0.8f)
                strokePaint.strokeWidth = max(2f, tcell * 0.10f)
                c.drawRoundRect(RectF(rect.left - tcell * 0.15f, rect.top - tcell * 0.15f,
                    rect.right + tcell * 0.15f, rect.bottom + tcell * 0.15f), tcell * 0.3f, tcell * 0.3f, strokePaint)
            }
        }

        if (solved) drawClear(c)
    }

    private fun layoutTray(r: Room) {
        trayRects.clear()
        val pad = trayHeight * 0.09f
        val availH = trayHeight - pad * 2f
        var units = 0.0
        for (item in r.tray) {
            val s = Pieces.of(item.piece).sizes[item.variant]
            units += s.x + 0.5
        }
        trayCellPx = Geo.trayCell((vw - pad * 2f).toInt(), availH.toInt(), units)
        val tcell = trayCellPx.toFloat()

        var total = 0f
        for (item in r.tray) {
            val s = Pieces.of(item.piece).sizes[item.variant]
            total += s.x * tcell + tcell * 0.5f
        }
        var x = (vw - total) / 2f
        for (item in r.tray) {
            val s = Pieces.of(item.piece).sizes[item.variant]
            val w = s.x * tcell
            val h = s.y * tcell
            val top = trayTop + pad + (availH - h) / 2f
            trayRects.add(RectF(x, top, x + w, top + h))
            x += w + tcell * 0.5f
        }
    }

    private fun drawClear(c: Canvas) {
        fillPaint.color = Palette.withAlpha(0xFF3A3730.toInt(), 0.32f)
        c.drawRect(0f, 0f, vw, vh, fillPaint)

        val pw = min(vw * 0.56f, vh * 1.15f)
        val ph = vh * 0.66f
        val panel = RectF((vw - pw) / 2f, (vh - ph) / 2f, (vw + pw) / 2f, (vh + ph) / 2f)
        fillPaint.color = Palette.PANEL
        c.drawRoundRect(panel, vh * 0.035f, vh * 0.035f, fillPaint)

        text(c, "ROOM $roomLevel CLEARED", panel.centerX(), panel.top + ph * 0.20f, ph * 0.11f, Palette.INK, true)

        if (roomLevel < Levels.COUNT) {
            val next = Levels.LEVELS[roomLevel]
            text(c, "PASSWORD FOR ROOM ${roomLevel + 1}", panel.centerX(), panel.top + ph * 0.40f, ph * 0.062f, Palette.INK_SOFT, false)
            text(c, next.password, panel.centerX(), panel.top + ph * 0.56f, ph * 0.15f, Palette.ACCENT, true)
        } else {
            text(c, "all sixty rooms are done", panel.centerX(), panel.top + ph * 0.46f, ph * 0.075f, Palette.ACCENT, true)
        }

        val bw = pw * 0.26f
        val bh = ph * 0.14f
        val gap = pw * 0.03f
        val y = panel.bottom - ph * 0.22f
        if (roomLevel < Levels.COUNT) {
            val total = bw * 3 + gap * 2
            var x = panel.centerX() - total / 2f
            button(c, RectF(x, y, x + bw, y + bh), "NEXT", "next", true)
            x += bw + gap
            button(c, RectF(x, y, x + bw, y + bh), "REPLAY", "replay", false)
            x += bw + gap
            button(c, RectF(x, y, x + bw, y + bh), "MENU", "toMenu", false)
        } else {
            val total = bw * 2 + gap
            var x = panel.centerX() - total / 2f
            button(c, RectF(x, y, x + bw, y + bh), "REPLAY", "replay", true)
            x += bw + gap
            button(c, RectF(x, y, x + bw, y + bh), "MENU", "toMenu", false)
        }
    }

    // ---------------------------------------------------------------- input

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event.x, event.y)
            MotionEvent.ACTION_MOVE -> onMove(event.x, event.y)
            MotionEvent.ACTION_UP -> onUp(event.x, event.y)
            MotionEvent.ACTION_CANCEL -> cancelDrag()
        }
        return true
    }

    private fun buttonAt(x: Float, y: Float): String? {
        for (i in hits.indices.reversed()) {
            if (hits[i].r.contains(x, y)) return hits[i].id
        }
        return null
    }

    private fun onDown(x: Float, y: Float) {
        when (screen) {
            Screen.TITLE -> when (buttonAt(x, y)) {
                "play" -> openRoom(unlocked)
                "rooms" -> { screen = Screen.ROOMS; invalidate() }
                "password" -> { password.setLength(0); passwordError = false; screen = Screen.PASSWORD; invalidate() }
                "exit" -> leaveApp()
            }

            Screen.PASSWORD -> onPasswordKey(buttonAt(x, y))

            Screen.ROOMS -> {
                val id = buttonAt(x, y)
                if (id == "back") { screen = Screen.TITLE; invalidate() }
                else if (id != null && id.startsWith("room:")) openRoom(id.substringAfter(':').toInt())
            }

            Screen.PLAY -> playDown(x, y)
        }
    }

    private fun onPasswordKey(id: String?) {
        when {
            id == null -> {}
            id == "back" -> { screen = Screen.TITLE; invalidate() }
            id == "del" -> { if (password.isNotEmpty()) password.setLength(password.length - 1); passwordError = false; invalidate() }
            id == "ok" -> submitPassword()
            id.startsWith("key:") -> {
                if (password.length < 4) password.append(id.substringAfter(':'))
                passwordError = false
                if (password.length == 4) submitPassword() else invalidate()
            }
        }
    }

    private fun submitPassword() {
        val word = password.toString().uppercase()
        if (word == Levels.UNLOCK_ALL_PASSWORD) {
            unlocked = Levels.COUNT
            prefs.edit().putInt("unlocked", unlocked).apply()
            screen = Screen.ROOMS
            invalidate()
            return
        }
        val level = Levels.PASSWORD_TO_LEVEL[word]
        if (level != null) {
            openRoom(level)
        } else {
            passwordError = true
            password.setLength(0)
            invalidate()
        }
    }

    private fun playDown(x: Float, y: Float) {
        val r = room ?: return

        if (solved) {
            when (buttonAt(x, y)) {
                "next" -> openRoom(min(roomLevel + 1, Levels.COUNT))
                "replay" -> openRoom(roomLevel)
                "toMenu" -> { screen = Screen.TITLE; invalidate() }
            }
            return
        }

        when (buttonAt(x, y)) {
            "hint" -> { useHint(); return }
            "rotate" -> { rotateActive(); return }
            "flip" -> { flipActive(); return }
            "reset" -> { r.reset(); solved = false; activeTray = null; activeBoard = null; invalidate(); return }
            "menu" -> { screen = Screen.TITLE; invalidate(); return }
        }

        // a piece in the tray
        for (i in r.tray.indices) {
            val rect = trayRects.getOrNull(i) ?: continue
            if (!rect.contains(x, y)) continue
            val item = r.tray[i]
            val s = Pieces.of(item.piece).sizes[item.variant]
            val tcell = trayCellPx.toFloat()
            val gx = ((x - rect.left) / tcell).toInt().coerceIn(0, s.x - 1)
            val gy = ((y - rect.top) / tcell).toInt().coerceIn(0, s.y - 1)
            activeTray = item
            activeBoard = null
            drag = DragState(item, null, gx, gy, x, y, x, y, item.variant, false)
            invalidate()
            return
        }

        // a piece already on the board
        val gx = ((x - boardX) / cellPx).toInt()
        val gy = ((y - boardY) / cellPx).toInt()
        val p = r.placementAt(gx, gy)
        if (p != null) {
            val grabX = gx - p.ox
            val grabY = gy - p.oy
            r.lift(p)
            activeTray = null
            activeBoard = p
            drag = DragState(null, p, grabX, grabY, x, y, x, y, p.variant, false)
            invalidate()
        }
    }

    private fun onMove(x: Float, y: Float) {
        val d = drag ?: return
        d.fx = x
        d.fy = y
        if (!d.moved && (abs(x - d.downX) > slop || abs(y - d.downY) > slop)) d.moved = true
        invalidate()
    }

    private fun onUp(x: Float, y: Float) {
        val d = drag ?: return
        drag = null
        val r = room
        if (r == null) { invalidate(); return }

        if (!d.moved) {
            tapPiece(d)
            invalidate()
            return
        }

        val (fox, foy) = dragOrigin(d)
        val ox = fox.roundToInt()
        val oy = foy.roundToInt()
        if (r.canPlace(d.piece, d.variant, ox, oy)) {
            r.place(d.piece, d.variant, ox, oy)
            if (d.item != null) r.tray.remove(d.item)
            click()
            afterPlace(r)
        } else {
            if (d.item != null) {
                d.item.variant = d.variant
            } else if (d.original != null) {
                r.place(d.original.piece, d.original.variant, d.original.ox, d.original.oy)
            }
        }
        invalidate()
    }

    private fun tapPiece(d: DragState) {
        val r = room ?: return
        val item = d.item
        if (item != null) {
            // tapping a tray piece turns it a quarter turn
            item.variant = Pieces.of(item.piece).rotated(item.variant)
            activeTray = item
            activeBoard = null
            click()
            return
        }
        val original = d.original ?: return
        val variant = Pieces.of(original.piece).rotated(original.variant)
        val placed = tryPlaceNear(r, original.piece, variant, original.ox, original.oy)
        if (placed == null) {
            r.place(original.piece, original.variant, original.ox, original.oy)
            activeBoard = null
        } else {
            activeBoard = placed
            click()
            afterPlace(r)
        }
    }

    private fun tryPlaceNear(r: Room, piece: Int, variant: Int, ox: Int, oy: Int): Placement? {
        val offsets = listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, -1 to 1, 1 to -1, -1 to -1)
        for ((dx, dy) in offsets) {
            if (r.place(piece, variant, ox + dx, oy + dy)) return r.placed.last()
        }
        return null
    }

    private fun dragOrigin(d: DragState): Pair<Float, Float> {
        val cell = cellPx.toFloat()
        return ((d.fx - d.grabX * cell - boardX) / cell) to ((d.fy - d.grabY * cell - boardY) / cell)
    }

    private fun cancelDrag() {
        val d = drag ?: return
        drag = null
        val r = room
        if (r != null && d.original != null) {
            r.place(d.original.piece, d.original.variant, d.original.ox, d.original.oy)
        }
        invalidate()
    }

    private fun rotateActive() {
        val r = room ?: return
        val d = drag
        if (d != null) {
            d.variant = Pieces.of(d.piece).rotated(d.variant)
            d.item?.variant = d.variant
            invalidate()
            return
        }
        val item = activeTray
        if (item != null) {
            item.variant = Pieces.of(item.piece).rotated(item.variant)
            click()
            invalidate()
            return
        }
        val p = activeBoard
        if (p != null) {
            r.lift(p)
            val variant = Pieces.of(p.piece).rotated(p.variant)
            val moved = tryPlaceNear(r, p.piece, variant, p.ox, p.oy)
            if (moved == null) {
                r.place(p.piece, p.variant, p.ox, p.oy)
                activeBoard = null
            } else {
                activeBoard = moved
                afterPlace(r)
            }
            invalidate()
        }
    }

    private fun flipActive() {
        val r = room ?: return
        val d = drag
        if (d != null) {
            d.variant = Pieces.of(d.piece).flipped(d.variant)
            d.item?.variant = d.variant
            invalidate()
            return
        }
        val item = activeTray
        if (item != null) {
            item.variant = Pieces.of(item.piece).flipped(item.variant)
            click()
            invalidate()
            return
        }
        val p = activeBoard
        if (p != null) {
            r.lift(p)
            val variant = Pieces.of(p.piece).flipped(p.variant)
            val moved = tryPlaceNear(r, p.piece, variant, p.ox, p.oy)
            if (moved == null) {
                r.place(p.piece, p.variant, p.ox, p.oy)
                activeBoard = null
            } else {
                activeBoard = moved
                afterPlace(r)
            }
            invalidate()
        }
    }

    /**
     * Puts one piece of a full solution on the board. A room allows this once; the hint is only
     * spent if a piece could actually be placed.
     */
    private fun useHint() {
        val r = room ?: return
        if (solved || !hintAvailable(roomLevel)) return
        val placed = Hint.place(r)
        if (placed == null) {
            showBanner("this layout cannot be finished - lift a piece and try again")
            return
        }
        hintsUsed.add(roomLevel)
        saveHints()
        activeTray = null
        activeBoard = placed
        click()
        afterPlace(r)
        invalidate()
    }

    private fun showBanner(message: String) {
        banner = message
        bannerUntil = SystemClock.elapsedRealtime() + 2800
        invalidate()
    }

    private fun afterPlace(r: Room) {
        if (r.isSolved() && !solved) {
            solved = true
            if (roomLevel < Levels.COUNT && unlocked < roomLevel + 1) {
                unlocked = roomLevel + 1
                prefs.edit().putInt("unlocked", unlocked).apply()
            }
        }
    }

    private fun openRoom(level: Int) {
        roomLevel = level.coerceIn(1, Levels.COUNT)
        room = Room(Levels.LEVELS[roomLevel - 1])
        solved = false
        activeTray = null
        activeBoard = null
        drag = null
        screen = Screen.PLAY
        invalidate()
    }

    /**
     * The system back gesture. Returns false when there is nowhere left to go, which lets the
     * activity close.
     */
    internal fun handleBack(): Boolean {
        return when (screen) {
            Screen.TITLE -> false
            Screen.PLAY -> {
                drag = null
                screen = Screen.TITLE
                invalidate()
                true
            }
            Screen.PASSWORD, Screen.ROOMS -> {
                screen = Screen.TITLE
                invalidate()
                true
            }
        }
    }

    /** The EXIT button: closes the activity, which is the whole app. */
    private fun leaveApp() {
        drag = null
        (context as? Activity)?.finish()
    }

    // ---------------------------------------------------------------- test hooks

    /** Opens the room select with every room available. Used by the screenshot test. */
    internal fun showRooms() {
        unlocked = Levels.COUNT
        screen = Screen.ROOMS
        invalidate()
    }

    internal fun showPassword() {
        password.setLength(0)
        passwordError = false
        screen = Screen.PASSWORD
        invalidate()
    }

    internal fun open(level: Int) = openRoom(level)

    /** Geometry of the last frame, so a test can aim a finger at the pieces. */
    internal class DebugGeometry(
        val boardX: Float,
        val boardY: Float,
        val cell: Int,
        val trayCell: Int,
        val trayRects: List<RectF>,
    )

    internal fun debugGeometry() = DebugGeometry(boardX, boardY, cellPx, trayCellPx, trayRects.toList())

    internal fun debugRoom(): Room? = room

    /** Shows the room cleared panel. Used by the screenshot test. */
    internal fun debugClear() {
        solved = true
        invalidate()
    }

    // ---------------------------------------------------------------- sound

    private fun click() {
        if (!toneTried) {
            toneTried = true
            tone = try {
                ToneGenerator(AudioManager.STREAM_MUSIC, 35)
            } catch (t: Throwable) {
                null
            }
        }
        try {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 25)
        } catch (t: Throwable) {
            // a device without a tone generator simply plays nothing
        }
    }

    companion object {
        const val BUILD_STAMP = "Mental Power v1.2.0"
    }
}
