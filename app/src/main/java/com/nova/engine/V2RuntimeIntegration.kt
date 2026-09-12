package com.nova.engine

import android.graphics.RectF
import kotlin.math.max

/**
 * Production runtime boundary for V2.
 *
 * The editor owns authoring data; this class owns simulation state.  A runtime
 * session is created from a scene, builds physics bodies once, advances them
 * with the deterministic PhysicsWorld2D fixed-step clock, then copies only the
 * resulting transforms back to the scene instance used by the play surface.
 * Rendering consumes immutable frame records rather than reaching into the
 * physics implementation.
 */
class V2RuntimeWorld(private val scene: V2Scene) {
    private val physics = PhysicsWorld2D()
    private val bodyIds = LinkedHashMap<String, Int>()
    private val dynamicNodes = LinkedHashSet<String>()
    private var nextBodyId = 1
    private var running = true

    init {
        physics.gravityY = 1600f
        physics.fixedStep = 1f / 60f
        buildBodies()
    }

    private fun buildBodies() {
        scene.nodes.forEach { node ->
            if (node.body == "NONE") return@forEach
            val id = nextBodyId++
            bodyIds[node.id] = id
            if (node.body == "DYNAMIC" || node.body == "KINEMATIC") dynamicNodes += node.id
            physics.add(
                PhysicsBody(
                    id = id,
                    bounds = Aabb(node.x, node.y, max(1f, node.width * node.scaleX), max(1f, node.height * node.scaleY)),
                    dynamic = node.body == "DYNAMIC",
                    gravityScale = 1f,
                    layer = 1,
                    mask = -1
                )
            )
        }
    }

    fun setRunning(value: Boolean) { running = value }
    fun isRunning(): Boolean = running
    fun physicsWorld(): PhysicsWorld2D = physics

    fun setMoveInput(horizontal: Float) {
        val player = scene.nodes.firstOrNull { it.type == "CharacterBody2D" } ?: return
        val body = bodyIds[player.id]?.let(physics::body) ?: return
        body.vx = horizontal.coerceIn(-1f, 1f) * 320f
    }

    fun jump() {
        val player = scene.nodes.firstOrNull { it.type == "CharacterBody2D" } ?: return
        val body = bodyIds[player.id]?.let(physics::body) ?: return
        if (body.grounded) body.vy = -620f
    }

    fun step(frameDelta: Float) {
        if (!running) return
        physics.step(frameDelta.coerceIn(0f, 0.25f))
        syncTransforms()
    }

    private fun syncTransforms() {
        scene.nodes.forEach { node ->
            val id = bodyIds[node.id] ?: return@forEach
            val body = physics.body(id) ?: return@forEach
            node.x = body.bounds.x
            node.y = body.bounds.y
        }
    }

    fun playerGrounded(): Boolean {
        val player = scene.nodes.firstOrNull { it.type == "CharacterBody2D" } ?: return false
        return bodyIds[player.id]?.let(physics::body)?.grounded == true
    }

    fun frame(viewport: RectF): V2RenderFrame {
        val drawables = scene.nodes
            .asSequence()
            .filter { it.visible }
            .sortedWith(compareBy<V2Node> { it.zIndex }.thenBy { it.y }.thenBy { it.id })
            .mapNotNull { node ->
                val w = max(1f, node.width * node.scaleX)
                val h = max(1f, node.height * node.scaleY)
                val bounds = RectF(node.x, node.y, node.x + w, node.y + h)
                if (!RectF.intersects(bounds, viewport)) return@mapNotNull null
                V2RenderItem(node.id, node.type, node.x, node.y, w, h, node.rotation, node.zIndex, node.texture, node.animation)
            }
            .toList()
        return V2RenderFrame(drawables, physics.all().size, physics.collisions().size, playerGrounded())
    }
}

data class V2RenderItem(
    val nodeId: String,
    val type: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotation: Float,
    val zIndex: Int,
    val texture: String,
    val animation: String
)

data class V2RenderFrame(
    val items: List<V2RenderItem>,
    val physicsBodies: Int,
    val contacts: Int,
    val playerGrounded: Boolean
)
