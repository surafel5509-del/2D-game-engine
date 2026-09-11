package com.nova.engine

import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** A touch-first Unity-style 2D editor shell. It intentionally has no external UI dependency. */
class NovaEditorView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans", Typeface.NORMAL) }
    private val project = EditorProject().apply { seedDemoContent() }
    private val entities = mutableListOf(
        EditorEntity(1, "Player", 320f, 220f),
        EditorEntity(2, "Ground", 320f, 430f, 520f, 64f, layer = -1, textureId = "tiles", tag = "StaticBody"),
        EditorEntity(3, "Light2D", 620f, 170f, 48f, 48f, layer = 2, textureId = "", tag = "Light")
    )
    private var selectedId = 1
    private var tool = "SELECT"
    private var playing = false
    private var showAssets = true
    private var showInspector = true
    private var showConsole = true
    private var status = "Ready"
    private var dragDx = 0f
    private var dragDy = 0f

    private val bg = Color.rgb(15, 18, 24)
    private val panel = Color.rgb(25, 29, 37)
    private val panel2 = Color.rgb(31, 36, 45)
    private val accent = Color.rgb(76, 145, 255)
    private val grid = Color.rgb(44, 49, 60)
    private val white = Color.rgb(225, 230, 238)
    private val muted = Color.rgb(145, 153, 166)

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(bg)
        val w = width.toFloat(); val h = height.toFloat()
        drawTopBar(c, w)
        val left = if (showAssets) 230f else 0f
        val right = if (showInspector) 285f else 0f
        val bottom = if (showConsole) 145f else 0f
        drawViewport(c, left, 46f, w - right, h - bottom)
        if (showAssets) drawLeftPanel(c, 0f, 46f, left, h - bottom)
        if (showInspector) drawInspector(c, w - right, 46f, w, h - bottom)
        if (showConsole) drawConsole(c, left, h - bottom, w - right, h)
    }

    private fun drawTopBar(c: Canvas, w: Float) {
        p.color = panel; c.drawRect(0f, 0f, w, 46f, p)
        label(c, "NOVA", 16f, 30f, 16f, white, true)
        label(c, "2D", 69f, 30f, 13f, accent, true)
        button(c, 105f, 7f, 68f, 32f, "☰", false)
        button(c, 179f, 7f, 66f, 32f, "▣", false)
        button(c, 251f, 7f, 66f, 32f, "⚙", false)
        button(c, 328f, 7f, 72f, 32f, "↶", false)
        button(c, 404f, 7f, 72f, 32f, "↷", false)
        button(c, 486f, 6f, 64f, 34f, if (playing) "■" else "▶", playing)
        button(c, 556f, 7f, 70f, 32f, "Ⅱ", false)
        button(c, 632f, 7f, 70f, 32f, "□", false)
        label(c, project.projectName, max(720f, w - 220f), 29f, 13f, muted, false)
    }

    private fun drawLeftPanel(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        p.color = panel; c.drawRect(l, t, r, b, p)
        label(c, "SCENE", 14f, t + 27f, 11f, muted, true)
        button(c, r - 70f, t + 10f, 28f, 26f, "+", false)
        button(c, r - 37f, t + 10f, 28f, 26f, "⋮", false)
        var y = t + 64f
        label(c, "▾  MainScene", 16f, y, 13f, white, true); y += 28f
        for (e in entities) {
            val selected = e.id == selectedId
            if (selected) { p.color = Color.rgb(47, 72, 105); c.drawRect(6f, y - 18f, r - 6f, y + 8f, p) }
            label(c, if (e.visible) "□" else "○", 18f, y, 12f, if (e.visible) white else muted, false)
            label(c, e.name, 42f, y, 13f, if (selected) white else Color.rgb(190,196,206), false)
            label(c, e.tag, r - 76f, y, 9f, muted, false)
            y += 30f
        }
        p.color = grid; c.drawRect(0f, b - 42f, r, b - 41f, p)
        label(c, "CREATE", 14f, b - 18f, 10f, muted, true)
        label(c, "＋ Sprite    ＋ Node2D    ＋ Camera", 72f, b - 18f, 10f, white, false)
    }

    private fun drawViewport(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        p.color = Color.rgb(18, 22, 29); c.drawRect(l, t, r, b, p)
        p.color = grid; p.strokeWidth = 1f
        var x = l + 20f
        while (x < r) { c.drawLine(x, t, x, b, p); x += 32f }
        var y = t + 20f
        while (y < b) { c.drawLine(l, y, r, y, p); y += 32f }
        label(c, "2D", l + 14f, t + 24f, 11f, muted, true)
        button(c, l + 52f, t + 7f, 34f, 28f, "↖", tool == "SELECT")
        button(c, l + 90f, t + 7f, 34f, 28f, "✚", tool == "MOVE")
        button(c, l + 128f, t + 7f, 34f, 28f, "↻", tool == "ROTATE")
        button(c, l + 166f, t + 7f, 34f, 28f, "□", tool == "SCALE")
        label(c, "100%", r - 58f, t + 24f, 10f, muted, false)
        for (e in entities.sortedBy { it.layer }) drawEntity(c, e, l, t)
        label(c, status, l + 14f, b - 14f, 10f, muted, false)
    }

    private fun drawEntity(c: Canvas, e: EditorEntity, l: Float, t: Float) {
        if (!e.visible) return
        val sx = l + e.x; val sy = t + e.y
        p.style = Paint.Style.FILL
        p.color = when (e.tag) { "Player" -> Color.rgb(75, 160, 235); "StaticBody" -> Color.rgb(88, 98, 112); else -> Color.rgb(245, 178, 70) }
        c.drawRoundRect(sx, sy, sx + e.width, sy + e.height, 8f, 8f, p)
        if (e.tag == "Player") {
            p.color = Color.WHITE; c.drawCircle(sx + e.width/2, sy + 30f, 13f, p)
            p.color = Color.rgb(30, 50, 70); c.drawCircle(sx + e.width/2 - 5, sy + 28f, 3f, p); c.drawCircle(sx + e.width/2 + 5, sy + 28f, 3f, p)
        } else if (e.tag == "Light") {
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Color.YELLOW; c.drawCircle(sx + 24f, sy + 24f, 22f, p); p.style = Paint.Style.FILL
        }
        if (e.id == selectedId) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = accent; c.drawRect(sx - 3f, sy - 3f, sx + e.width + 3f, sy + e.height + 3f, p)
            p.style = Paint.Style.FILL
            label(c, e.name, sx, sy - 8f, 10f, accent, true)
        }
    }

    private fun drawInspector(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        p.color = panel; c.drawRect(l, t, r, b, p)
        val e = entities.firstOrNull { it.id == selectedId }
        label(c, "INSPECTOR", l + 14f, t + 27f, 11f, muted, true)
        if (e == null) return
        label(c, e.name, l + 14f, t + 61f, 16f, white, true)
        label(c, "Node2D / ${e.tag}", l + 14f, t + 80f, 10f, muted, false)
        section(c, "TRANSFORM", l + 14f, t + 112f, r - 14f)
        field(c, l + 14f, t + 136f, "Position X", "%.1f".format(e.x)); field(c, l + 14f, t + 171f, "Position Y", "%.1f".format(e.y))
        field(c, l + 14f, t + 206f, "Rotation", "0.0°"); field(c, l + 14f, t + 241f, "Scale", "1.0 × 1.0")
        section(c, "SPRITE", l + 14f, t + 289f, r - 14f)
        field(c, l + 14f, t + 313f, "Texture", e.textureId.ifEmpty { "None" }); field(c, l + 14f, t + 348f, "Size", "${e.width.toInt()} × ${e.height.toInt()}")
        section(c, "RENDER", l + 14f, t + 396f, r - 14f)
        field(c, l + 14f, t + 420f, "Layer", e.layer.toString()); field(c, l + 14f, t + 455f, "Visible", if (e.visible) "true" else "false")
        section(c, "PHYSICS", l + 14f, t + 503f, r - 14f)
        field(c, l + 14f, t + 527f, "Body", if (e.tag == "StaticBody") "Static" else "Dynamic")
    }

    private fun drawConsole(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        p.color = panel; c.drawRect(l, t, r, b, p)
        label(c, "CONSOLE", l + 14f, t + 23f, 10f, muted, true)
        label(c, "✓  Editor initialized", l + 14f, t + 48f, 11f, Color.rgb(120, 210, 145), false)
        label(c, "✓  Native runtime connected", l + 14f, t + 68f, 11f, Color.rgb(120, 210, 145), false)
        label(c, "•  Scene: ${project.sceneName}    •  Entities: ${entities.size}    •  Assets: ${project.assets.size}", l + 14f, t + 88f, 11f, muted, false)
        label(c, "FPS 60    |    Draw Calls 3    |    Memory 18 MB", l + 14f, t + 110f, 10f, muted, false)
        button(c, r - 88f, t + 10f, 72f, 28f, "Clear", false)
    }

    private fun section(c: Canvas, title: String, x: Float, y: Float, r: Float) { p.color = grid; c.drawRect(x, y + 10f, r, y + 11f, p); label(c, title, x, y, 9f, muted, true) }
    private fun field(c: Canvas, x: Float, y: Float, name: String, value: String) { label(c, name, x, y + 16f, 10f, muted, false); p.color = panel2; c.drawRoundRect(x + 92f, y, x + 255f, y + 26f, 4f, 4f, p); label(c, value, x + 102f, y + 17f, 10f, white, false) }
    private fun label(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean) { text.textSize = size; text.color = color; text.typeface = Typeface.create("sans", if (bold) Typeface.BOLD else Typeface.NORMAL); c.drawText(s, x, y, text) }
    private fun button(c: Canvas, x: Float, y: Float, w: Float, h: Float, s: String, active: Boolean) { p.color = if (active) Color.rgb(48, 93, 150) else panel2; c.drawRoundRect(x, y, x+w, y+h, 5f, 5f, p); label(c, s, x + w/2f - text.measureText(s)/2f, y + h/2f + 4f, 11f, white, false) }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.x; val y = ev.y
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                val t = y - 46f; val left = if (showAssets) 230f else 0f
                val bottom = if (showConsole) 145f else 0f
                if (y < 46f && x in 486f..550f) { playing = !playing; status = if (playing) "Playing" else "Stopped"; invalidate(); return true }
                if (showAssets && x < 230f && y > 95f && y < 260f) {
                    val index = ((y - 110f) / 30f).toInt(); if (index in entities.indices) { selectedId = entities[index].id; status = "Selected ${entities[index].name}"; invalidate() }; return true
                }
                if (x >= left && x < width - (if (showInspector) 285 else 0) && y > 46f && y < height-bottom) {
                    val worldX = x-left; val worldY = y-46f
                    entities.reversed().firstOrNull { worldX >= it.x && worldX <= it.x+it.width && worldY >= it.y && worldY <= it.y+it.height }?.let { selectedId = it.id; dragDx = worldX-it.x; dragDy=worldY-it.y; invalidate() }
                    return true
                }
                if (showInspector && x > width-285f && y > 120f) { status = "Inspector ready — edit fields"; invalidate(); return true }
            }
            MotionEvent.ACTION_MOVE -> {
                if (tool == "MOVE" || selectedId != 0) {
                    val left = if (showAssets) 230f else 0f
                    val e = entities.firstOrNull { it.id == selectedId }
                    if (e != null && x >= left && x < width-(if(showInspector)285 else 0) && y>46f) { e.x=(x-left-dragDx).coerceIn(0f,1000f); e.y=(y-46f-dragDy).coerceIn(0f,700f); status="Editing ${e.name}"; invalidate() }
                }
            }
        }
        return true
    }
}
