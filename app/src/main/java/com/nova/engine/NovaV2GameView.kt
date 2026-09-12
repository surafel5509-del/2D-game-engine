package com.nova.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * V2 playable runtime surface.
 *
 * Play mode is backed by V2RuntimeWorld: the same scene data authored by the
 * editor is converted to physics state, stepped at a fixed 60 Hz cadence, and
 * exposed to rendering through V2RenderFrame records.  No hard-coded demo
 * world or private player simulation is created here.
 */
class NovaV2GameView(
    private val owner: MainActivity,
    private val scene: V2Scene
) : View(owner) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val runtime = V2RuntimeWorld(scene)
    private var left = false
    private var right = false
    private var jumpQueued = false
    private var lastNs = System.nanoTime()
    private var gameTime = 0f
    private var cameraX = 0f
    private var cameraY = 0f

    init {
        isFocusable = true
        setBackgroundColor(Color.rgb(8, 11, 17))
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val now = System.nanoTime()
        val dt = ((now - lastNs) / 1e9f).coerceIn(0f, .10f)
        lastNs = now
        gameTime += dt

        runtime.setMoveInput(when {
            left && !right -> -1f
            right && !left -> 1f
            else -> 0f
        })
        if (jumpQueued) {
            runtime.jump()
            jumpQueued = false
        }
        runtime.step(dt)
        updateCamera()
        drawWorld(c)
        drawHud(c)
        postInvalidateOnAnimation()
    }

    private fun updateCamera() {
        val player = scene.nodes.firstOrNull { it.type == "CharacterBody2D" } ?: return
        val targetX = player.x + player.width * player.scaleX * .5f
        val targetY = player.y + player.height * player.scaleY * .5f
        cameraX += (targetX - width * .5f - cameraX) * .12f
        cameraY += (targetY - height * .5f - cameraY) * .12f
    }

    private fun drawWorld(c: Canvas) {
        c.drawColor(Color.rgb(10, 15, 24))
        paint.color = Color.rgb(25, 42, 62)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val viewport = RectF(cameraX, cameraY, cameraX + width, cameraY + height)
        val frame = runtime.frame(viewport)
        frame.items.forEach { item ->
            val sx = item.x - cameraX
            val sy = item.y - cameraY
            paint.style = Paint.Style.FILL
            paint.color = when (item.type) {
                "CharacterBody2D" -> Color.rgb(60, 155, 235)
                "StaticBody2D" -> Color.rgb(76, 91, 110)
                "Light2D" -> Color.rgb(236, 183, 60)
                "Camera2D" -> Color.rgb(108, 119, 139)
                else -> Color.rgb(76, 120, 155)
            }
            c.save()
            c.rotate(item.rotation, sx + item.width / 2f, sy + item.height / 2f)
            c.drawRoundRect(RectF(sx, sy, sx + item.width, sy + item.height), 10f, 10f, paint)
            if (item.type == "CharacterBody2D") {
                paint.color = Color.WHITE
                c.drawCircle(sx + item.width / 2f, sy + minOf(26f, item.height / 2f), minOf(11f, item.width / 4f), paint)
            }
            if (item.type == "Light2D") {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.color = Color.YELLOW
                c.drawCircle(sx + item.width / 2f, sy + item.height / 2f, minOf(item.width, item.height) / 2f, paint)
            }
            c.restore()
        }
    }

    private fun drawHud(c: Canvas) {
        val player = scene.nodes.firstOrNull { it.type == "CharacterBody2D" }
        val contacts = runtime.physicsWorld().collisions().size
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(190, 7, 10, 15)
        c.drawRect(0f, 0f, width.toFloat(), 64f, paint)
        paint.color = Color.WHITE
        paint.textSize = 18f
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        c.drawText("NOVA V2  •  PLAYING", 20f, 30f, paint)
        paint.textSize = 10f
        paint.typeface = android.graphics.Typeface.DEFAULT
        c.drawText("Physics 60Hz  •  Bodies ${runtime.physicsWorld().all().size}  •  Contacts $contacts", 20f, 49f, paint)
        c.drawText(if (runtime.playerGrounded()) "GROUNDED" else "AIRBORNE", width - 105f, 30f, paint)
        if (player != null) {
            c.drawText("X ${player.x.toInt()}  Y ${player.y.toInt()}", width - 150f, 49f, paint)
        }

        paint.color = Color.argb(180, 7, 10, 15)
        c.drawRoundRect(RectF(18f, height - 86f, 150f, height - 18f), 16f, 16f, paint)
        c.drawRoundRect(RectF(width - 150f, height - 86f, width - 18f, height - 18f), 16f, 16f, paint)
        paint.color = Color.WHITE
        paint.textSize = 13f
        c.drawText("◀   MOVE", 42f, height - 46f, paint)
        c.drawText("MOVE   ▶", width - 120f, height - 46f, paint)

        paint.color = Color.argb(220, 35, 45, 60)
        c.drawRoundRect(RectF(width * .5f - 44f, height - 92f, width * .5f + 44f, height - 18f), 18f, 18f, paint)
        paint.color = Color.WHITE
        c.drawText("JUMP", width * .5f - 17f, height - 49f, paint)

        paint.color = Color.argb(225, 35, 45, 60)
        c.drawRoundRect(RectF(width - 122f, 78f, width - 18f, 124f), 12f, 12f, paint)
        paint.color = Color.WHITE
        c.drawText("BACK", width - 88f, 107f, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                if (e.x > width - 140f && e.y in 68f..140f) {
                    owner.exitGame()
                    return true
                }
                left = e.x < width * .28f
                right = e.x > width * .72f
                if (e.y > height * .72f && e.x in width * .38f..width * .62f) jumpQueued = true
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                left = false
                right = false
                return true
            }
        }
        return true
    }
}
