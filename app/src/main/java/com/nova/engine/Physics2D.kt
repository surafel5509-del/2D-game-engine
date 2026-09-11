package com.nova.engine

import kotlin.math.abs
import kotlin.math.max

/** Lightweight deterministic 2D collision/character physics layer. */
data class Aabb(var x: Float, var y: Float, var width: Float, var height: Float)
data class PhysicsBody(var id: Int, var bounds: Aabb, var vx: Float = 0f, var vy: Float = 0f, var dynamic: Boolean = true, var gravityScale: Float = 1f, var layer: Int = 1, var mask: Int = -1, var grounded: Boolean = false)

data class Collision2D(val a: Int, val b: Int, val normalX: Float, val normalY: Float, val penetration: Float)

class PhysicsWorld2D {
    var gravityY = 1600f
    var fixedStep = 1f / 60f
    private val bodies = LinkedHashMap<Int, PhysicsBody>()
    private var accumulator = 0f
    private val contacts = mutableListOf<Collision2D>()

    fun add(body: PhysicsBody) { bodies[body.id] = body }
    fun remove(id: Int) { bodies.remove(id) }
    fun body(id: Int): PhysicsBody? = bodies[id]
    fun all(): List<PhysicsBody> = bodies.values.toList()
    fun collisions(): List<Collision2D> = contacts.toList()

    fun step(dt: Float) {
        accumulator = minOf(accumulator + dt, 0.25f)
        contacts.clear()
        while (accumulator >= fixedStep) { tick(fixedStep); accumulator -= fixedStep }
    }

    private fun tick(dt: Float) {
        bodies.values.forEach { b ->
            b.grounded = false
            if (b.dynamic) { b.vy += gravityY * b.gravityScale * dt; b.bounds.x += b.vx * dt; b.bounds.y += b.vy * dt }
        }
        val list = bodies.values.toList()
        for (i in list.indices) for (j in i + 1 until list.size) {
            val a = list[i]; val b = list[j]
            if (!a.dynamic && !b.dynamic) continue
            if ((a.mask and b.layer) == 0 || (b.mask and a.layer) == 0) continue
            resolve(a, b)
        }
    }

    private fun resolve(a: PhysicsBody, b: PhysicsBody) {
        if (!overlap(a.bounds, b.bounds)) return
        val ax = a.bounds.x + a.bounds.width / 2f; val ay = a.bounds.y + a.bounds.height / 2f
        val bx = b.bounds.x + b.bounds.width / 2f; val by = b.bounds.y + b.bounds.height / 2f
        val dx = bx - ax; val dy = by - ay
        val px = (a.bounds.width + b.bounds.width) / 2f - abs(dx)
        val py = (a.bounds.height + b.bounds.height) / 2f - abs(dy)
        if (px < py) {
            val n = if (dx >= 0) 1f else -1f; val p = max(0f, px)
            if (a.dynamic && b.dynamic) { a.bounds.x -= n*p/2; b.bounds.x += n*p/2 } else if (a.dynamic) a.bounds.x -= n*p else if (b.dynamic) b.bounds.x += n*p
            if (a.dynamic) a.vx = 0f; if (b.dynamic) b.vx = 0f
            contacts += Collision2D(a.id,b.id,n,0f,p)
        } else {
            val n = if (dy >= 0) 1f else -1f; val p = max(0f, py)
            if (a.dynamic && b.dynamic) { a.bounds.y -= n*p/2; b.bounds.y += n*p/2 } else if (a.dynamic) a.bounds.y -= n*p else if (b.dynamic) b.bounds.y += n*p
            if (a.dynamic) { if (n < 0) a.grounded = true; a.vy = if (n < 0) 0f else a.vy }
            if (b.dynamic) { if (n > 0) b.grounded = true; b.vy = if (n > 0) 0f else b.vy }
            contacts += Collision2D(a.id,b.id,0f,n,p)
        }
    }

    private fun overlap(a: Aabb, b: Aabb): Boolean = a.x < b.x+b.width && a.x+a.width > b.x && a.y < b.y+b.height && a.y+a.height > b.y
}
