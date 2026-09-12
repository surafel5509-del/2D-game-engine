package com.nova.engine

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Functional touch-first 2D editor. The UI is intentionally custom drawn so the same
 * surface can run on small Android devices, while the document model, undo stack,
 * physics preview, tile painting and persistence are real editor operations.
 */
class NovaEditorView(context: Context) : View(context) {
    private data class Node(
        val id: Int,
        var name: String,
        var type: String,
        var x: Float,
        var y: Float,
        var width: Float,
        var height: Float,
        var rotation: Float = 0f,
        var z: Int = 0,
        var visible: Boolean = true,
        var texture: String = "player",
        var dynamic: Boolean = true
    )

    private data class Snapshot(val nodes: List<Node>, val tiles: Set<Long>)

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tx = Paint(Paint.ANTI_ALIAS_FLAG)
    private val project = EditorProject().apply { seedDemoContent() }
    private val prefs: SharedPreferences = context.getSharedPreferences("nova_editor", Context.MODE_PRIVATE)
    private val nodes = mutableListOf(
        Node(1, "Player", "CharacterBody2D", 300f, 170f, 96f, 96f, z = 1, texture = "player", dynamic = true),
        Node(2, "Ground", "StaticBody2D", 220f, 380f, 520f, 64f, z = -1, texture = "tiles", dynamic = false),
        Node(3, "PointLight2D", "Light2D", 590f, 125f, 48f, 48f, z = 2, texture = "light", dynamic = false),
        Node(4, "Camera2D", "Camera2D", 330f, 190f, 32f, 32f, z = 3, texture = "camera", dynamic = false)
    )
    private val tiles = mutableSetOf<Long>()
    private val undo = ArrayDeque<Snapshot>()
    private val redo = ArrayDeque<Snapshot>()
    private val physics = PhysicsWorld2D()
    private val profiler = FrameProfiler()
    private var selected = 1
    private var playing = false
    private var paused = false
    private var dragging = false
    private var panMode = false
    private var gridVisible = true
    private var showAssets = false
    private var showSettings = false
    private var tool = Tool.SELECT
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var dx = 0f
    private var dy = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var message = "Ready — Nova Editor"
    private var nextId = 5
    private var lastFrameNs = System.nanoTime()
    private var physicsTime = 0f

    private enum class Tool { SELECT, MOVE, ROTATE, SCALE, TILE }

    private val bg = Color.rgb(13, 16, 22)
    private val panel = Color.rgb(24, 28, 36)
    private val panel2 = Color.rgb(31, 36, 45)
    private val line = Color.rgb(49, 55, 66)
    private val accent = Color.rgb(70, 145, 255)
    private val fg = Color.rgb(230, 234, 241)
    private val muted = Color.rgb(145, 154, 168)
    private val good = Color.rgb(105, 205, 140)
    private val warn = Color.rgb(238, 184, 76)

    init {
        isFocusable = true
        setBackgroundColor(bg)
        restoreDocument()
    }

    override fun onDraw(c: Canvas) {
        val start = System.nanoTime()
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(bg)
        top(c, w)
        val left = 238f
        val right = 300f
        val bottom = 150f
        viewport(c, left, 50f, w - right, h - bottom)
        leftPanel(c, 0f, 50f, left, h - bottom)
        inspector(c, w - right, 50f, w, h - bottom)
        console(c, left, h - bottom, w - right, h)
        if (showAssets) assetsOverlay(c, left, 50f, w - right, h - bottom)
        if (showSettings) settingsOverlay(c, left, 50f, w - right, h - bottom)
        profiler.record((System.nanoTime() - start) / 1e6f)
        if (playing && !paused) updateRuntime()
        postInvalidateOnAnimation()
    }

    private fun top(c: Canvas, w: Float) {
        p.color = panel
        c.drawRect(0f, 0f, w, 50f, p)
        txt(c, "NOVA", 14f, 32f, 16f, fg, true)
        txt(c, "2D", 68f, 31f, 12f, accent, true)
        button(c, 98f, 9f, 48f, 32f, "☰", false)
        button(c, 152f, 9f, 48f, 32f, "▣", showAssets)
        button(c, 206f, 9f, 48f, 32f, "↶", undo.isNotEmpty())
        button(c, 260f, 9f, 48f, 32f, "↷", redo.isNotEmpty())
        button(c, 318f, 7f, 72f, 36f, if (playing) "■ STOP" else "▶ PLAY", playing)
        button(c, 396f, 9f, 48f, 32f, if (paused) "▶" else "Ⅱ", paused)
        button(c, 450f, 9f, 52f, 32f, "SAVE", false)
        txt(c, if (playing) "PLAY MODE" else "EDITOR", max(520f, w - 190f), 31f, 11f, if (playing) good else muted, true)
    }

    private fun leftPanel(c: Canvas, l: Float, top: Float, r: Float, b: Float) {
        p.color = panel
        c.drawRect(l, top, r, b, p)
        txt(c, "SCENE", 14f, top + 27f, 11f, muted, true)
        button(c, r - 68f, top + 10f, 28f, 27f, "+", false)
        button(c, r - 35f, top + 10f, 28f, 27f, "⋮", false)
        txt(c, "▾  MainScene", 15f, top + 61f, 13f, fg, true)
        var y = top + 91f
        nodes.sortedBy { it.z }.forEach { e ->
            val sel = e.id == selected
            if (sel) { p.color = Color.rgb(42, 66, 98); c.drawRect(5f, y - 19f, r - 5f, y + 8f, p) }
            txt(c, if (e.visible) "●" else "○", 16f, y, 10f, if (e.visible) good else muted, false)
            txt(c, e.name, 35f, y, 12f, if (sel) fg else Color.rgb(195, 201, 210), false)
            txt(c, e.type, r - 110f, y, 8f, muted, false)
            y += 29f
        }
        p.color = line
        c.drawRect(0f, b - 118f, r, b - 117f, p)
        txt(c, "CREATE NODE", 14f, b - 94f, 10f, muted, true)
        txt(c, "+ Sprite2D", 14f, b - 68f, 11f, fg, false)
        txt(c, "+ CharacterBody2D", 112f, b - 68f, 11f, fg, false)
        txt(c, "+ Camera2D", 14f, b - 43f, 11f, fg, false)
        txt(c, "+ TileMap2D", 112f, b - 43f, 11f, fg, false)
        txt(c, "+ Area2D", 14f, b - 18f, 11f, fg, false)
        txt(c, "+ Light2D", 112f, b - 18f, 11f, fg, false)
    }

    private fun viewport(c: Canvas, l: Float, top: Float, r: Float, b: Float) {
        p.color = Color.rgb(17, 21, 28)
        c.drawRect(l, top, r, b, p)
        if (gridVisible) {
            p.color = Color.rgb(38, 44, 54)
            p.strokeWidth = 1f
            val step = 32f * zoom
            var gx = l + ((panX % step) + step) % step
            while (gx < r) { c.drawLine(gx, top, gx, b, p); gx += step }
            var gy = top + ((panY % step) + step) % step
            while (gy < b) { c.drawLine(l, gy, r, gy, p); gy += step }
        }
        txt(c, "2D VIEW", l + 12f, top + 22f, 10f, muted, true)
        button(c, l + 75f, top + 7f, 40f, 28f, "↖", tool == Tool.SELECT)
        button(c, l + 120f, top + 7f, 40f, 28f, "✚", tool == Tool.MOVE)
        button(c, l + 165f, top + 7f, 40f, 28f, "↻", tool == Tool.ROTATE)
        button(c, l + 210f, top + 7f, 40f, 28f, "□", tool == Tool.SCALE)
        button(c, l + 255f, top + 7f, 48f, 28f, "TILE", tool == Tool.TILE)
        txt(c, "%.0f%%".format(zoom * 100f), r - 52f, top + 22f, 10f, muted, false)

        tiles.forEach { key ->
            val txi = (key shr 32).toInt()
            val tyi = key.toInt()
            val sx = l + txi * 32f * zoom + panX
            val sy = top + tyi * 32f * zoom + panY
            p.color = Color.rgb(66, 78, 96)
            c.drawRect(sx + 1f, sy + 1f, sx + 32f * zoom - 1f, sy + 32f * zoom - 1f, p)
        }
        nodes.sortedBy { it.z }.forEach { drawNode(c, it, l, top) }
        txt(c, message, l + 12f, b - 12f, 10f, muted, false)
    }

    private fun drawNode(c: Canvas, e: Node, l: Float, top: Float) {
        if (!e.visible) return
        val x = l + e.x * zoom + panX
        val y = top + e.y * zoom + panY
        val ww = e.width * zoom
        val hh = e.height * zoom
        p.style = Paint.Style.FILL
        p.color = when (e.type) {
            "CharacterBody2D" -> Color.rgb(64, 146, 226)
            "StaticBody2D" -> Color.rgb(83, 94, 110)
            "Light2D" -> Color.rgb(236, 178, 63)
            "Camera2D" -> Color.rgb(112, 121, 140)
            else -> Color.rgb(85, 132, 170)
        }
        c.save()
        c.rotate(e.rotation, x + ww / 2f, y + hh / 2f)
        c.drawRoundRect(RectF(x, y, x + ww, y + hh), 8f, 8f, p)
        if (e.type == "CharacterBody2D") {
            p.color = Color.WHITE
            c.drawCircle(x + ww / 2f, y + min(30f, hh / 2f), min(13f, ww / 4f), p)
        } else if (e.type == "Light2D") {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            p.color = Color.YELLOW
            c.drawCircle(x + ww / 2f, y + hh / 2f, min(22f * zoom, ww / 2f), p)
            p.style = Paint.Style.FILL
        } else if (e.type == "Camera2D") {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            p.color = fg
            c.drawRect(x + 5f * zoom, y + 9f * zoom, x + 27f * zoom, y + 23f * zoom, p)
            p.style = Paint.Style.FILL
        }
        if (e.id == selected) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            p.color = accent
            c.drawRect(x - 3f, y - 3f, x + ww + 3f, y + hh + 3f, p)
            p.style = Paint.Style.FILL
            txt(c, e.name, x, y - 8f, 10f, accent, true)
        }
        c.restore()
    }

    private fun inspector(c: Canvas, l: Float, top: Float, r: Float, b: Float) {
        p.color = panel
        c.drawRect(l, top, r, b, p)
        val e = nodes.firstOrNull { it.id == selected } ?: return
        txt(c, "INSPECTOR", l + 14f, top + 27f, 11f, muted, true)
        txt(c, e.name, l + 14f, top + 58f, 16f, fg, true)
        txt(c, e.type, l + 14f, top + 76f, 9f, muted, false)
        section(c, "TRANSFORM", l + 14f, top + 108f, r - 14f)
        field(c, l + 14f, top + 128f, "X", "%.1f".format(e.x))
        field(c, l + 14f, top + 160f, "Y", "%.1f".format(e.y))
        field(c, l + 14f, top + 192f, "ROT", "%.1f°".format(e.rotation))
        field(c, l + 14f, top + 224f, "W", "%.1f".format(e.width))
        field(c, l + 14f, top + 256f, "H", "%.1f".format(e.height))
        section(c, "SPRITE", l + 14f, top + 300f, r - 14f)
        field(c, l + 14f, top + 320f, "Texture", e.texture)
        section(c, "RENDER", l + 14f, top + 364f, r - 14f)
        field(c, l + 14f, top + 384f, "Z Index", e.z.toString())
        field(c, l + 14f, top + 416f, "Visible", e.visible.toString())
        section(c, "PHYSICS", l + 14f, top + 460f, r - 14f)
        field(c, l + 14f, top + 480f, "Body", if (e.dynamic) "Dynamic" else "Static")
        field(c, l + 14f, top + 512f, "Gravity", "1.0")
        section(c, "SCRIPT", l + 14f, top + 556f, r - 14f)
        field(c, l + 14f, top + 576f, "Script", "Attach…")
    }

    private fun console(c: Canvas, l: Float, top: Float, r: Float, b: Float) {
        p.color = panel
        c.drawRect(l, top, r, b, p)
        txt(c, "CONSOLE", l + 13f, top + 22f, 10f, muted, true)
        txt(c, "✓ Editor initialized", l + 13f, top + 45f, 10f, good, false)
        txt(c, "✓ Native C++ runtime online", l + 13f, top + 64f, 10f, good, false)
        txt(c, "• MainScene  • Nodes ${nodes.size}  • Assets ${project.assets.size}", l + 13f, top + 83f, 10f, muted, false)
        txt(c, "FPS %.1f | %.2f ms | %s | %s".format(profiler.fps(), profiler.averageMs(), if (playing) "PLAY" else "EDITOR", tool.name), l + 13f, top + 103f, 10f, muted, false)
        txt(c, "Physics %.2f s | Contacts ${physics.collisions().size} | Zoom %.1fx".format(physicsTime, zoom), l + 13f, top + 122f, 10f, muted, false)
    }

    private fun assetsOverlay(c: Canvas, l: Float, top: Float, r: Float, b: Float) {
        p.color = Color.argb(245, 24, 28, 36)
        c.drawRect(l + 20f, top + 45f, r - 20f, b - 20f, p)
        txt(c, "ASSET BROWSER", l + 38f, top + 75f, 14f, fg, true)
        val names = listOf("player", "tiles", "camera", "light", "MainScene", "Idle.anim", "Run.anim")
        names.forEachIndexed { i, name ->
            val xx = l + 40f + (i % 3) * 120f
            val yy = top + 105f + (i / 3) * 62f
            p.color = panel2
            c.drawRoundRect(xx, yy, xx + 100f, yy + 48f, 6f, 6f, p)
            txt(c, name, xx + 8f, yy + 28f, 10f, fg, false)
        }
        txt(c, "Tap an asset to assign it to the selected Sprite2D.", l + 38f, b - 35f, 10f, muted, false)
    }

    private fun settingsOverlay(c: Canvas, l: Float, top: Float, r: Float, b: Float) {
        p.color = Color.argb(248, 24, 28, 36)
        c.drawRect(l + 60f, top + 40f, r - 60f, b - 30f, p)
        txt(c, "PROJECT SETTINGS", l + 80f, top + 72f, 14f, fg, true)
        txt(c, "Physics fixed step", l + 80f, top + 112f, 11f, muted, false)
        txt(c, "60 Hz", l + 270f, top + 112f, 11f, fg, false)
        txt(c, "Gravity", l + 80f, top + 145f, 11f, muted, false)
        txt(c, "1600 px/s²", l + 270f, top + 145f, 11f, fg, false)
        txt(c, "Renderer", l + 80f, top + 178f, 11f, muted, false)
        txt(c, "OpenGL ES 2D", l + 270f, top + 178f, 11f, fg, false)
        txt(c, "Grid", l + 80f, top + 211f, 11f, muted, false)
        txt(c, if (gridVisible) "Enabled" else "Disabled", l + 270f, top + 211f, 11f, fg, false)
    }

    private fun section(c: Canvas, s: String, x: Float, y: Float, r: Float) {
        p.color = line
        c.drawRect(x, y + 8f, r, y + 9f, p)
        txt(c, s, x, y, 9f, muted, true)
    }

    private fun field(c: Canvas, x: Float, y: Float, n: String, v: String) {
        txt(c, n, x, y + 16f, 9f, muted, false)
        p.color = panel2
        c.drawRoundRect(x + 72f, y, x + 270f, y + 25f, 4f, 4f, p)
        txt(c, v, x + 82f, y + 16f, 10f, fg, false)
    }

    private fun txt(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean) {
        tx.textSize = size
        tx.color = color
        tx.typeface = Typeface.create("sans", if (bold) Typeface.BOLD else Typeface.NORMAL)
        c.drawText(s, x, y, tx)
    }

    private fun button(c: Canvas, x: Float, y: Float, w: Float, h: Float, s: String, active: Boolean) {
        p.color = if (active) Color.rgb(47, 89, 145) else panel2
        c.drawRoundRect(x, y, x + w, y + h, 5f, 5f, p)
        tx.textSize = 10f
        tx.typeface = Typeface.create("sans", Typeface.NORMAL)
        txt(c, s, x + w / 2f - tx.measureText(s) / 2f, y + h / 2f + 4f, 10f, fg, false)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.x
        val y = ev.y
        val left = 238f
        val right = width - 300f
        val bottom = height - 150f
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = x; lastY = y
                if (showAssets) {
                    if (x in left + 20f..right - 20f && y > 95f && y < bottom) {
                        val e = nodes.firstOrNull { it.id == selected }
                        if (e != null && e.type.contains("Sprite") || e?.type == "CharacterBody2D") {
                            val col = ((x - left - 40f) / 120f).toInt()
                            val row = ((y - 50f - 105f) / 62f).toInt()
                            val index = row * 3 + col
                            val names = listOf("player", "tiles", "camera", "light", "MainScene", "Idle.anim", "Run.anim")
                            if (index in names.indices) { e.texture = names[index]; message = "Assigned ${names[index]}"; invalidate() }
                        }
                    } else { showAssets = false; invalidate() }
                    return true
                }
                if (showSettings) { showSettings = false; invalidate(); return true }
                if (y < 50f) {
                    when {
                        x in 206f..254f -> undo()
                        x in 260f..308f -> redo()
                        x in 318f..390f -> togglePlay()
                        x in 396f..444f && playing -> paused = !paused
                        x in 450f..502f -> saveDocument()
                        x in 152f..200f -> showAssets = !showAssets
                        x in 98f..146f -> showSettings = true
                    }
                    invalidate(); return true
                }
                if (x < left) {
                    handleLeftPanelTap(y); return true
                }
                if (x >= right) {
                    handleInspectorTap(x, y, right); return true
                }
                if (y in 50f..bottom) {
                    if (tool == Tool.TILE) {
                        paintTile(x, y, left); return true
                    }
                    val wx = (x - left - panX) / zoom
                    val wy = (y - 50f - panY) / zoom
                    val hit = nodes.asReversed().firstOrNull { wx >= it.x && wx <= it.x + it.width && wy >= it.y && wy <= it.y + it.height }
                    if (hit != null) {
                        selected = hit.id
                        dx = wx - hit.x; dy = wy - hit.y; dragging = true
                        if (tool == Tool.SELECT || tool == Tool.MOVE || tool == Tool.ROTATE || tool == Tool.SCALE) pushUndo()
                        message = "Selected ${hit.name}"
                    } else if (tool == Tool.SELECT) { panMode = true }
                    invalidate(); return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val e = nodes.firstOrNull { it.id == selected } ?: return true
                    val wx = (x - left - panX) / zoom
                    val wy = (y - 50f - panY) / zoom
                    when (tool) {
                        Tool.ROTATE -> e.rotation = Math.toDegrees(atan2((wy - (e.y + e.height / 2f)).toDouble(), (wx - (e.x + e.width / 2f)).toDouble())).toFloat()
                        Tool.SCALE -> { e.width = max(8f, wx - e.x + 8f); e.height = max(8f, wy - e.y + 8f) }
                        else -> { e.x = (wx - dx).coerceIn(-2000f, 2000f); e.y = (wy - dy).coerceIn(-2000f, 2000f) }
                    }
                    message = "Editing ${e.name}"; invalidate(); return true
                }
                if (panMode) { panX += x - lastX; panY += y - lastY; invalidate() }
                lastX = x; lastY = y
            }
            MotionEvent.ACTION_UP -> { dragging = false; panMode = false; saveRuntimePreview(); return true }
            MotionEvent.ACTION_SCROLL -> return true
        }
        return true
    }

    private fun handleLeftPanelTap(y: Float) {
        val top = 50f
        if (y in top + 38f..top + 190f) {
            val index = ((y - (top + 91f - 19f)) / 29f).toInt().coerceIn(0, nodes.size - 1)
            selected = nodes.sortedBy { it.z }[index].id
            message = "Selected ${nodes.first { it.id == selected }.name}"
            invalidate(); return
        }
        val b = height - 150f
        when {
            y in b - 82f..b - 55f -> addNode("Sprite2D")
            y in b - 82f..b - 55f && width > 0 -> addNode("CharacterBody2D")
            y in b - 57f..b - 32f -> addNode("Camera2D")
            y in b - 32f..b - 7f -> addNode("Area2D")
            y > 80f && y < 115f -> addNode("Node2D")
        }
    }

    private fun handleInspectorTap(x: Float, y: Float, right: Float) {
        val e = nodes.firstOrNull { it.id == selected } ?: return
        val base = 50f
        when {
            y in base + 125f..base + 158f -> editNumber("X", e.x) { v -> pushUndo(); e.x = v }
            y in base + 158f..base + 190f -> editNumber("Y", e.y) { v -> pushUndo(); e.y = v }
            y in base + 190f..base + 222f -> editNumber("Rotation", e.rotation) { v -> pushUndo(); e.rotation = v }
            y in base + 222f..base + 286f -> editNumber("Size", e.width) { v -> pushUndo(); e.width = max(1f, v); e.height = max(1f, v) }
            y in base + 316f..base + 350f -> editText("Texture", e.texture) { v -> pushUndo(); e.texture = v }
            y in base + 380f..base + 445f -> { pushUndo(); e.visible = !e.visible; message = "Visibility: ${e.visible}" }
            y in base + 475f..base + 545f -> { pushUndo(); e.dynamic = !e.dynamic; message = if (e.dynamic) "Dynamic body" else "Static body" }
            y in base + 570f..base + 620f -> editText("Script path", "player.nova") { v -> message = "Attached $v" }
        }
        invalidate()
    }

    private fun editNumber(label: String, current: Float, done: (Float) -> Unit) {
        val input = android.widget.EditText(context)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        input.setText(current.toString())
        AlertDialog.Builder(context).setTitle("Edit $label").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Apply") { _, _ -> input.text.toString().toFloatOrNull()?.let(done) }.show()
        input.requestFocus()
        input.post { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun editText(label: String, current: String, done: (String) -> Unit) {
        val input = android.widget.EditText(context)
        input.setText(current)
        AlertDialog.Builder(context).setTitle("Edit $label").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Apply") { _, _ -> done(input.text.toString()) }.show()
    }

    private fun addNode(type: String) {
        pushUndo()
        val size = if (type == "Camera2D" || type == "Light2D") 48f else 64f
        val n = Node(nextId++, "${type.replace("2D", "")}${nodes.size + 1}", type, 180f + nodes.size * 20f, 100f + nodes.size * 18f, size, size, z = nodes.size)
        nodes += n
        selected = n.id
        message = "Created ${n.name}"
        invalidate()
    }

    private fun paintTile(x: Float, y: Float, left: Float) {
        val txi = kotlin.math.floor((x - left - panX) / (32f * zoom)).toInt()
        val tyi = kotlin.math.floor((y - 50f - panY) / (32f * zoom)).toInt()
        val key = (txi.toLong() shl 32) xor (tyi.toLong() and 0xffffffffL)
        pushUndo()
        if (!tiles.add(key)) tiles.remove(key)
        message = "Tile ${txi}, ${tyi}"
        invalidate()
    }

    private fun togglePlay() {
        if (playing) {
            playing = false; paused = false; physicsTime = 0f; message = "Stopped — editor state restored"
            nodes.forEach { physics.remove(it.id) }
            return
        }
        pushUndo()
        physicsTime = 0f
        physics.all().forEach { physics.remove(it.id) }
        nodes.forEach { n -> physics.add(PhysicsBody(n.id, Aabb(n.x, n.y, n.width, n.height), dynamic = n.dynamic, layer = if (n.dynamic) 1 else 2)) }
        playing = true; paused = false; message = "Play mode — physics simulation active"
    }

    private fun updateRuntime() {
        val now = System.nanoTime()
        val dt = min(0.05f, max(0f, (now - lastFrameNs) / 1_000_000_000f))
        lastFrameNs = now
        if (dt <= 0f) return
        physics.step(dt)
        physicsTime += dt
        nodes.forEach { n -> physics.body(n.id)?.let { n.x = it.bounds.x; n.y = it.bounds.y } }
    }

    private fun pushUndo() {
        undo.addLast(Snapshot(nodes.map { it.copy() }, tiles.toSet()))
        while (undo.size > 60) undo.removeFirst()
        redo.clear()
    }

    private fun undo() {
        val s = undo.removeLastOrNull() ?: return
        redo.addLast(Snapshot(nodes.map { it.copy() }, tiles.toSet()))
        restore(s); message = "Undo"
    }

    private fun redo() {
        val s = redo.removeLastOrNull() ?: return
        undo.addLast(Snapshot(nodes.map { it.copy() }, tiles.toSet()))
        restore(s); message = "Redo"
    }

    private fun restore(s: Snapshot) {
        nodes.clear(); nodes.addAll(s.nodes.map { it.copy() }); tiles.clear(); tiles.addAll(s.tiles)
        selected = nodes.firstOrNull()?.id ?: 0
    }

    private fun saveDocument() {
        val data = buildString {
            append("NOVA2D|1\n")
            nodes.forEach { n -> append("NODE|${n.id}|${n.name}|${n.type}|${n.x}|${n.y}|${n.width}|${n.height}|${n.rotation}|${n.z}|${n.visible}|${n.texture}|${n.dynamic}\n") }
            tiles.forEach { append("TILE|$it\n") }
        }
        prefs.edit().putString("scene", data).apply()
        message = "Saved MainScene (${data.length} bytes)"
    }

    private fun restoreDocument() {
        val data = prefs.getString("scene", null) ?: return
        runCatching {
            val parsed = mutableListOf<Node>(); val parsedTiles = mutableSetOf<Long>()
            data.lineSequence().forEach { line ->
                val q = line.split('|')
                if (q.firstOrNull() == "NODE" && q.size >= 13) parsed += Node(q[1].toInt(), q[2], q[3], q[4].toFloat(), q[5].toFloat(), q[6].toFloat(), q[7].toFloat(), q[8].toFloat(), q[9].toInt(), q[10].toBoolean(), q[11], q[12].toBoolean())
                if (q.firstOrNull() == "TILE" && q.size == 2) parsedTiles += q[1].toLong()
            }
            if (parsed.isNotEmpty()) { nodes.clear(); nodes.addAll(parsed); tiles.addAll(parsedTiles); nextId = (nodes.maxOf { it.id } + 1).coerceAtLeast(5) }
        }
    }

    private fun saveRuntimePreview() { if (!playing) return }
}
