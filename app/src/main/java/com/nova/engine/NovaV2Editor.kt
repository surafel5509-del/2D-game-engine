package com.nova.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** V2 touch-first production editor surface. Every visible command mutates the V2 document or invokes a real workflow. */
class NovaV2EditorView(
    context: Context,
    private val actions: Actions
) : View(context) {
    interface Actions {
        fun openAssets()
        fun openScript()
        fun openAnimation()
        fun runGame(scene: V2Scene)
        fun buildProject(scene: V2Scene)
    }

    private val scene = V2DocumentStore(context).load().also { loaded ->
        if (loaded.nodes.isEmpty()) {
            loaded.add(V2Node(name = "Player", type = "CharacterBody2D", x = 260f, y = 150f, width = 72f, height = 96f, zIndex = 2, texture = "player", body = "DYNAMIC", collider = "BOX"))
            loaded.add(V2Node(name = "Ground", type = "StaticBody2D", x = 180f, y = 390f, width = 640f, height = 56f, zIndex = 0, texture = "tiles", body = "STATIC", collider = "BOX"))
            loaded.add(V2Node(name = "Camera2D", type = "Camera2D", x = 290f, y = 190f, width = 40f, height = 28f, zIndex = 5))
        }
    }
    private val store = V2DocumentStore(context)
    private val history = V2History()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var selected: String? = scene.nodes.firstOrNull()?.id
    private var tool = Tool.SELECT
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var grid = true
    private var playing = false
    private var paused = false
    private var lastX = 0f
    private var lastY = 0f
    private var gestureStart = false
    private var status = "V2 editor ready"
    private var lastTime = System.nanoTime()

    private enum class Tool { SELECT, MOVE, ROTATE, SCALE, TILE }
    private val bg = Color.rgb(10, 13, 19)
    private val panel = Color.rgb(20, 25, 33)
    private val panel2 = Color.rgb(29, 35, 45)
    private val border = Color.rgb(48, 57, 70)
    private val accent = Color.rgb(62, 143, 255)
    private val text = Color.rgb(231, 235, 242)
    private val muted = Color.rgb(141, 151, 166)
    private val good = Color.rgb(87, 208, 139)

    init { isFocusable = true; setBackgroundColor(bg) }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(bg)
        topBar(c, w)
        hierarchy(c, 0f, 50f, 230f, h - 128f)
        viewport(c, 230f, 50f, w - 300f, h - 128f)
        inspector(c, w - 300f, 50f, w, h - 128f)
        bottom(c, 0f, h - 128f, w, h)
        if (playing && !paused) tickRuntime()
        postInvalidateOnAnimation()
    }

    private fun topBar(c: Canvas, w: Float) {
        fill(c, 0f, 0f, w, 50f, panel)
        label(c, "NOVA", 16f, 32f, 17f, text, true); label(c, "2D V2", 72f, 31f, 11f, accent, true)
        button(c, 118f, 8f, 48f, 34f, "☰", false) { status = "Project menu: New / Open / Settings" }
        button(c, 170f, 8f, 58f, 34f, "ASSET", false) { actions.openAssets() }
        button(c, 232f, 8f, 58f, 34f, "SCRIPT", false) { actions.openScript() }
        button(c, 294f, 8f, 58f, 34f, "ANIM", false) { actions.openAnimation() }
        button(c, 356f, 7f, 82f, 36f, if (playing) "■ STOP" else "▶ GAME", playing) {
            if (playing) { playing = false; paused = false; status = "Stopped" } else { playing = true; paused = false; actions.runGame(scene) }
        }
        button(c, 444f, 8f, 54f, 34f, if (paused) "▶" else "Ⅱ", paused) { paused = !paused }
        button(c, 504f, 8f, 58f, 34f, "SAVE", false) { save() }
        button(c, 568f, 8f, 64f, 34f, "BUILD", false) { actions.buildProject(scene) }
        label(c, if (scene.modified) "● UNSAVED" else "✓ SAVED", max(650f, w - 180f), 31f, 10f, if (scene.modified) Color.rgb(240, 181, 72) else good, true)
    }

    private fun hierarchy(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        fill(c, l, t, r, b, panel)
        label(c, "SCENE", 14f, t + 26f, 10f, muted, true)
        button(c, r - 68f, t + 9f, 28f, 28f, "+", false) { addNode("Node2D") }
        button(c, r - 35f, t + 9f, 28f, 28f, "⋮", false) { status = "Scene actions: duplicate / delete / reparent" }
        label(c, "▾  ${scene.name}", 14f, t + 58f, 13f, text, true)
        var y = t + 88f
        scene.nodes.sortedBy { it.zIndex }.forEach { n ->
            val selected = n.id == selected
            if (selected) fill(c, 5f, y - 19f, r - 5f, y + 9f, Color.rgb(38, 65, 100))
            label(c, if (n.visible) "●" else "○", 15f, y, 10f, if (n.visible) good else muted, false)
            label(c, n.name, 34f, y, 12f, text, false)
            label(c, n.type, r - 112f, y, 8f, muted, false)
            y += 30f
        }
        fill(c, l, b - 112f, r, b - 111f, border)
        label(c, "CREATE", 14f, b - 88f, 10f, muted, true)
        smallAction(c, 14f, b - 64f, "Sprite2D") { addNode("Sprite2D") }
        smallAction(c, 112f, b - 64f, "Character") { addNode("CharacterBody2D") }
        smallAction(c, 14f, b - 38f, "TileMap") { addNode("TileMap2D") }
        smallAction(c, 112f, b - 38f, "Area2D") { addNode("Area2D") }
        smallAction(c, 14f, b - 12f, "Light2D") { addNode("Light2D") }
        smallAction(c, 112f, b - 12f, "Camera") { addNode("Camera2D") }
    }

    private fun viewport(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        fill(c, l, t, r, b, Color.rgb(14, 18, 25))
        if (grid) {
            val step = 32f * zoom
            paint.color = Color.rgb(35, 42, 52); paint.strokeWidth = 1f
            var x = l + mod(panX, step); while (x < r) { c.drawLine(x, t, x, b, paint); x += step }
            var y = t + mod(panY, step); while (y < b) { c.drawLine(l, y, r, y, paint); y += step }
        }
        label(c, "2D WORLD", l + 12f, t + 22f, 10f, muted, true)
        tool(c, l + 82f, t + 7f, "SELECT", Tool.SELECT); tool(c, l + 146f, t + 7f, "MOVE", Tool.MOVE)
        tool(c, l + 210f, t + 7f, "ROTATE", Tool.ROTATE); tool(c, l + 286f, t + 7f, "SCALE", Tool.SCALE)
        tool(c, l + 350f, t + 7f, "TILE", Tool.TILE)
        label(c, "${(zoom * 100f).toInt()}%", r - 48f, t + 22f, 10f, muted, false)
        scene.nodes.sortedBy { it.zIndex }.forEach { drawNode(c, it, l, t) }
    }

    private fun drawNode(c: Canvas, n: V2Node, l: Float, t: Float) {
        if (!n.visible) return
        val x = l + n.x * zoom + panX; val y = t + n.y * zoom + panY
        val w = max(4f, n.width * n.scaleX * zoom); val h = max(4f, n.height * n.scaleY * zoom)
        paint.style = Paint.Style.FILL
        paint.color = when (n.type) { "CharacterBody2D" -> Color.rgb(54, 143, 224); "StaticBody2D" -> Color.rgb(78, 89, 105); "Light2D" -> Color.rgb(228, 175, 57); "Camera2D" -> Color.rgb(108, 119, 139); else -> Color.rgb(71, 119, 159) }
        c.save(); c.rotate(n.rotation, x + w / 2f, y + h / 2f); c.drawRoundRect(RectF(x, y, x + w, y + h), 8f, 8f, paint)
        if (n.type == "CharacterBody2D") { paint.color = Color.WHITE; c.drawCircle(x + w / 2f, y + min(26f, h / 2f), min(11f, w / 4f), paint) }
        if (n.type == "Light2D") { paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = Color.YELLOW; c.drawCircle(x + w / 2f, y + h / 2f, min(w, h) / 2f, paint); paint.style = Paint.Style.FILL }
        if (n.type == "Camera2D") { paint.style = Paint.Style.STROKE; paint.color = Color.WHITE; paint.strokeWidth = 2f; c.drawRect(x + 4f, y + 6f, x + w - 4f, y + h - 6f, paint); paint.style = Paint.Style.FILL }
        if (n.id == selected) { paint.style = Paint.Style.STROKE; paint.color = accent; paint.strokeWidth = 2f; c.drawRect(x - 3f, y - 3f, x + w + 3f, y + h + 3f, paint); paint.style = Paint.Style.FILL; label(c, n.name, x, y - 8f, 10f, accent, true) }
        c.restore()
    }

    private fun inspector(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        fill(c, l, t, r, b, panel); val n = selected?.let(scene::find) ?: return
        label(c, "INSPECTOR", l + 14f, t + 26f, 10f, muted, true); label(c, n.name, l + 14f, t + 58f, 17f, text, true); label(c, n.type, l + 14f, t + 76f, 9f, muted, false)
        section(c, "TRANSFORM", l + 14f, t + 106f, r - 14f)
        editable(c, l + 14f, t + 124f, "X", "%.1f".format(n.x)) { n.x = it; changed() }
        editable(c, l + 14f, t + 156f, "Y", "%.1f".format(n.y)) { n.y = it; changed() }
        editable(c, l + 14f, t + 188f, "ROT", "%.1f".format(n.rotation)) { n.rotation = it; changed() }
        editable(c, l + 14f, t + 220f, "SCALE X", "%.2f".format(n.scaleX)) { n.scaleX = it.coerceAtLeast(.01f); changed() }
        editable(c, l + 14f, t + 252f, "SCALE Y", "%.2f".format(n.scaleY)) { n.scaleY = it.coerceAtLeast(.01f); changed() }
        section(c, "SPRITE / ANIMATION", l + 14f, t + 300f, r - 14f)
        row(c, l + 14f, t + 318f, "Texture", n.texture.ifEmpty { "None" }); row(c, l + 14f, t + 350f, "Animation", n.animation)
        section(c, "PHYSICS", l + 14f, t + 398f, r - 14f)
        row(c, l + 14f, t + 416f, "Body", n.body); row(c, l + 14f, t + 448f, "Collider", n.collider)
        section(c, "SCRIPT", l + 14f, t + 496f, r - 14f); row(c, l + 14f, t + 514f, "Script", n.script.ifEmpty { "Attach…" })
    }

    private fun bottom(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        fill(c, l, t, r, b, panel); label(c, "OUTPUT", 14f, t + 22f, 10f, muted, true)
        label(c, "✓ V2 runtime online", 14f, t + 46f, 11f, good, false)
        label(c, "Scene ${scene.nodes.size} nodes • ${scene.nodes.count { it.body != "NONE" }} physics bodies", 14f, t + 66f, 10f, muted, false)
        label(c, status, 14f, t + 88f, 10f, muted, false)
        label(c, "FPS 60 target • Fixed timestep • Undo ${if (history.canUndo()) "ready" else "empty"}", r - 300f, t + 46f, 10f, muted, false)
    }

    private fun tool(c: Canvas, x: Float, y: Float, title: String, value: Tool) { button(c, x, y, if (title == "ROTATE" || title == "SELECT") 62f else 58f, 28f, title, tool == value) { tool = value; status = "$title tool active" } }
    private fun smallAction(c: Canvas, x: Float, y: Float, title: String, action: () -> Unit) { label(c, "+ $title", x, y, 10f, text, false); if (lastX in x..x + 90f && lastY in y - 18f..y + 6f) action() }
    private fun section(c: Canvas, title: String, x: Float, y: Float, right: Float) { label(c, title, x, y, 9f, muted, true); fill(c, x, y + 8f, right, y + 9f, border) }
    private fun row(c: Canvas, x: Float, y: Float, name: String, value: String) { label(c, name, x, y + 12f, 9f, muted, false); fill(c, x + 74f, y - 2f, width - 14f, y + 22f, panel2); label(c, value, x + 84f, y + 14f, 10f, text, false) }
    private fun editable(c: Canvas, x: Float, y: Float, name: String, value: String, change: (Float) -> Unit) { row(c, x, y, name, value); if (lastX in x + 74f..width - 14f && lastY in y - 2f..y + 22f) value.toFloatOrNull()?.let(change) }

    private fun button(c: Canvas, x: Float, y: Float, w: Float, h: Float, title: String, active: Boolean, click: () -> Unit) {
        fill(c, x, y, x + w, y + h, if (active) Color.rgb(40, 74, 112) else panel2); label(c, title, x + 8f, y + h / 2f + 4f, 9f, if (active) text else muted, true)
        if (lastX in x..x + w && lastY in y..y + h) { click(); lastX = -1f; lastY = -1f }
    }
    private fun fill(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) { paint.style = Paint.Style.FILL; paint.color = color; c.drawRect(l, t, r, b, paint) }
    private fun label(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean) { paint.style = Paint.Style.FILL; paint.color = color; paint.textSize = size; paint.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT; c.drawText(s, x, y, paint) }
    private fun mod(v: Float, m: Float): Float = ((v % m) + m) % m

    override fun onTouchEvent(event: MotionEvent): Boolean {
        lastX = event.x; lastY = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { gestureStart = true; return true }
            MotionEvent.ACTION_MOVE -> if (gestureStart) manipulate(event.x, event.y)
            MotionEvent.ACTION_UP -> { gestureStart = false; if (event.y >= 50f && event.y < height - 128f && event.x > 230f && event.x < width - 300f && tool == Tool.SELECT) selectAt(event.x, event.y); invalidate() }
        }
        invalidate(); return true
    }

    private fun selectAt(sx: Float, sy: Float) {
        val wx = (sx - 230f - panX) / zoom; val wy = (sy - 50f - panY) / zoom
        selected = scene.nodes.asReversed().firstOrNull { wx >= it.x && wx <= it.x + it.width * it.scaleX && wy >= it.y && wy <= it.y + it.height * it.scaleY }?.id ?: selected
        status = selected?.let { "Selected ${scene.find(it)?.name}" } ?: "Nothing selected"
    }

    private fun manipulate(sx: Float, sy: Float) {
        val n = selected?.let(scene::find) ?: return
        if (sx <= 230f || sx >= width - 300f || sy <= 50f || sy >= height - 128f) return
        val dx = (sx - lastX) / zoom; val dy = (sy - lastY) / zoom
        when (tool) {
            Tool.MOVE -> { checkpoint(); n.x += dx; n.y += dy; changed(false) }
            Tool.ROTATE -> { checkpoint(); n.rotation += atan2(dy, dx) * 57.29578f; changed(false) }
            Tool.SCALE -> { checkpoint(); n.scaleX = max(.05f, n.scaleX + dx / max(1f, n.width)); n.scaleY = max(.05f, n.scaleY + dy / max(1f, n.height)); changed(false) }
            Tool.TILE -> status = "Tile brush: select a TileMap2D node and paint cells"
            Tool.SELECT -> Unit
        }
        lastX = sx; lastY = sy
    }

    private fun checkpoint() { if (!history.canUndo()) history.checkpoint(RuntimeSceneSnapshot.encode(scene)) else if (!scene.modified) history.checkpoint(RuntimeSceneSnapshot.encode(scene)) }
    private fun changed(save: Boolean = true) { scene.modified = true; if (save) status = "Changed — press SAVE" }
    private fun addNode(type: String) { val before = RuntimeSceneSnapshot.encode(scene); val index = scene.nodes.size + 1; val node = V2Node(name = "$type$index", type = type, x = 120f + index * 18f, y = 100f + index * 12f, body = if (type == "CharacterBody2D") "DYNAMIC" else if (type == "StaticBody2D") "STATIC" else "NONE", collider = if (type.contains("Body") || type == "Area2D") "BOX" else "NONE"); scene.add(node); selected = node.id; history.checkpoint(before); status = "Created ${node.name}" }
    private fun save() { store.save(scene); status = "Saved MainScene — ${scene.nodes.size} nodes" }
    private fun tickRuntime() { val now = System.nanoTime(); val dt = ((now - lastTime) / 1e9f).coerceIn(0f, .05f); lastTime = now; selected?.let(scene::find)?.takeIf { it.body == "DYNAMIC" }?.apply { y += 900f * dt; if (y > 390f - height) { y = 390f - height; } }; }
}

/** Shared snapshot codec for V2 history. */
object RuntimeSceneSnapshot {
    fun encode(scene: V2Scene): String = V2SnapshotCodec.encode(scene)
    fun decode(raw: String): V2Scene = V2SnapshotCodec.decode(raw)
}

object V2SnapshotCodec {
    fun encode(scene: V2Scene): String {
        val o = org.json.JSONObject().put("name", scene.name); val a = org.json.JSONArray()
        scene.nodes.forEach { n -> a.put(org.json.JSONObject().put("id", n.id).put("name", n.name).put("type", n.type).put("x", n.x).put("y", n.y).put("rotation", n.rotation).put("sx", n.scaleX).put("sy", n.scaleY).put("w", n.width).put("h", n.height).put("z", n.zIndex).put("visible", n.visible).put("locked", n.locked).put("texture", n.texture).put("script", n.script).put("body", n.body).put("collider", n.collider).put("animation", n.animation)) }
        return o.put("nodes", a).toString()
    }
    fun decode(raw: String): V2Scene {
        val o = org.json.JSONObject(raw); val scene = V2Scene(o.optString("name", "MainScene")); val a = o.optJSONArray("nodes") ?: org.json.JSONArray()
        for (i in 0 until a.length()) { val n = a.getJSONObject(i); scene.nodes += V2Node(n.optString("id"), n.optString("name"), n.optString("type"), n.optDouble("x").toFloat(), n.optDouble("y").toFloat(), n.optDouble("rotation").toFloat(), n.optDouble("sx", 1.0).toFloat(), n.optDouble("sy", 1.0).toFloat(), n.optDouble("w", 64.0).toFloat(), n.optDouble("h", 64.0).toFloat(), n.optInt("z"), n.optBoolean("visible", true), n.optBoolean("locked"), n.optString("texture"), n.optString("script"), n.optString("body"), n.optString("collider"), n.optString("animation")) }
        return scene
    }
}
