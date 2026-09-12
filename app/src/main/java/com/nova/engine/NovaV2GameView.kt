package com.nova.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/** Full-screen playable runtime preview. It uses the same V2 scene data as the editor, not a separate demo scene. */
class NovaV2GameView(
    private val owner: MainActivity,
    private val scene: V2Scene
) : View(owner) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val player = scene.nodes.firstOrNull { it.type == "CharacterBody2D" } ?: scene.nodes.firstOrNull()
    private var left = false
    private var right = false
    private var jump = false
    private var velocityX = 0f
    private var velocityY = 0f
    private var lastNs = System.nanoTime()
    private var gameTime = 0f

    init { isFocusable = true; setBackgroundColor(Color.rgb(8, 11, 17)) }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val now = System.nanoTime(); val dt = ((now - lastNs) / 1e9f).coerceIn(0f, .033f); lastNs = now; gameTime += dt
        update(dt); drawWorld(c); drawHud(c); postInvalidateOnAnimation()
    }

    private fun update(dt: Float) {
        val n = player ?: return
        val input = when { left && !right -> -1f; right && !left -> 1f; else -> 0f }
        velocityX += (input * 900f - velocityX) * minOf(1f, dt * 10f)
        if (jump && n.y >= 290f) { velocityY = -620f; jump = false }
        velocityY += 1500f * dt
        n.x += velocityX * dt; n.y += velocityY * dt
        val ground = scene.nodes.firstOrNull { it.type == "StaticBody2D" }
        val floor = ground?.y ?: 390f
        if (n.y + n.height * n.scaleY >= floor) { n.y = floor - n.height * n.scaleY; velocityY = 0f }
        n.x = n.x.coerceIn(0f, max(0f, 1200f - n.width * n.scaleX))
    }

    private fun drawWorld(c: Canvas) {
        c.drawColor(Color.rgb(10, 15, 24))
        paint.color = Color.rgb(25, 42, 62); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        scene.nodes.sortedBy { it.zIndex }.forEach { n ->
            if (!n.visible) return@forEach
            val sx = n.x; val sy = n.y; val sw = n.width * n.scaleX; val sh = n.height * n.scaleY
            paint.color = when (n.type) { "CharacterBody2D" -> Color.rgb(60, 155, 235); "StaticBody2D" -> Color.rgb(76, 91, 110); "Light2D" -> Color.rgb(236, 183, 60); else -> Color.rgb(76, 120, 155) }
            c.save(); c.rotate(n.rotation, sx + sw / 2f, sy + sh / 2f); c.drawRoundRect(RectF(sx, sy, sx + sw, sy + sh), 10f, 10f, paint); c.restore()
            if (n.type == "CharacterBody2D") { paint.color = Color.WHITE; c.drawCircle(sx + sw / 2f, sy + 25f, 10f, paint) }
        }
    }

    private fun drawHud(c: Canvas) {
        paint.color = Color.argb(190, 7, 10, 15); c.drawRect(0f, 0f, width.toFloat(), 58f, paint)
        paint.color = Color.WHITE; paint.textSize = 18f; paint.typeface = android.graphics.Typeface.DEFAULT_BOLD; c.drawText("NOVA V2  •  PLAYING", 20f, 36f, paint)
        paint.textSize = 11f; paint.typeface = android.graphics.Typeface.DEFAULT; c.drawText("Touch controls  •  ← / → move  •  ↑ jump", 22f, height - 22f, paint)
        paint.color = Color.argb(220, 35, 45, 60); c.drawRoundRect(RectF(width - 132f, height - 70f, width - 18f, height - 18f), 14f, 14f, paint)
        paint.color = Color.WHITE; paint.textSize = 12f; c.drawText("BACK", width - 96f, height - 39f, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) {
            if (e.x > width - 150f && e.y > height - 95f) { owner.exitGame(); return true }
            left = e.x < width * .30f; right = e.x > width * .30f && e.x < width * .62f; jump = e.y > height * .68f && e.x < width * .82f
            return true
        }
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) { left = false; right = false; return true }
        return true
    }
}
