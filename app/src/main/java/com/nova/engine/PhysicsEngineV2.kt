package com.nova.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Nova Physics V2.
 *
 * This file is intentionally self-contained: the editor can describe bodies,
 * while the runtime owns mutable simulation state. The implementation uses a
 * fixed-step accumulator, swept integration, spatial hashing, SAT-style box
 * contacts, impulse resolution, friction, restitution, sensors and simple
 * constraints. It is deterministic for identical input and fixed-step data.
 */

// -----------------------------------------------------------------------------
// Math
// -----------------------------------------------------------------------------

data class PhysicsVec2(var x: Float = 0f, var y: Float = 0f) {
    fun set(x: Float, y: Float): PhysicsVec2 { this.x = x; this.y = y; return this }
    fun add(v: PhysicsVec2): PhysicsVec2 { x += v.x; y += v.y; return this }
    fun sub(v: PhysicsVec2): PhysicsVec2 { x -= v.x; y -= v.y; return this }
    fun mul(s: Float): PhysicsVec2 { x *= s; y *= s; return this }
    fun lengthSquared(): Float = x * x + y * y
    fun length(): Float = sqrt(lengthSquared())
    fun normalize(): PhysicsVec2 {
        val l = length()
        if (l > 1e-6f) { x /= l; y /= l }
        return this
    }
    fun copyOf(): PhysicsVec2 = PhysicsVec2(x, y)
}

private fun dot(a: PhysicsVec2, b: PhysicsVec2): Float = a.x * b.x + a.y * b.y
private fun cross(a: PhysicsVec2, b: PhysicsVec2): Float = a.x * b.y - a.y * b.x
private fun cross(s: Float, v: PhysicsVec2): PhysicsVec2 = PhysicsVec2(-s * v.y, s * v.x)
private fun rotate(v: PhysicsVec2, radians: Float): PhysicsVec2 {
    val c = cos(radians); val s = sin(radians)
    return PhysicsVec2(v.x * c - v.y * s, v.x * s + v.y * c)
}
private fun clamp(v: Float, lo: Float, hi: Float): Float = max(lo, min(hi, v))

// -----------------------------------------------------------------------------
// Shapes and materials
// -----------------------------------------------------------------------------

enum class PhysicsShapeType { BOX, CIRCLE, CAPSULE }

data class PhysicsMaterial(
    var density: Float = 1f,
    var friction: Float = 0.7f,
    var restitution: Float = 0.05f
) {
    fun sanitized(): PhysicsMaterial = PhysicsMaterial(
        density.coerceAtLeast(0f),
        friction.coerceIn(0f, 1f),
        restitution.coerceIn(0f, 1f)
    )
}

data class PhysicsShape(
    val type: PhysicsShapeType,
    var halfWidth: Float = 0.5f,
    var halfHeight: Float = 0.5f,
    var radius: Float = 0.5f,
    var capsuleHalfLength: Float = 0.5f
) {
    fun normalized(): PhysicsShape = copy(
        halfWidth = halfWidth.coerceAtLeast(0.001f),
        halfHeight = halfHeight.coerceAtLeast(0.001f),
        radius = radius.coerceAtLeast(0.001f),
        capsuleHalfLength = capsuleHalfLength.coerceAtLeast(0f)
    )
}

data class PhysicsFilter(
    var categoryBits: Int = 1,
    var maskBits: Int = -1,
    var groupIndex: Int = 0
) {
    fun canCollide(other: PhysicsFilter): Boolean {
        if (groupIndex != 0 && groupIndex == other.groupIndex) return groupIndex > 0
        return (maskBits and other.categoryBits) != 0 && (other.maskBits and categoryBits) != 0
    }
}

data class PhysicsAabb(
    var minX: Float,
    var minY: Float,
    var maxX: Float,
    var maxY: Float
) {
    fun overlaps(o: PhysicsAabb): Boolean =
        minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY
    fun expanded(amount: Float): PhysicsAabb = PhysicsAabb(minX - amount, minY - amount, maxX + amount, maxY + amount)
    fun center(): PhysicsVec2 = PhysicsVec2((minX + maxX) * 0.5f, (minY + maxY) * 0.5f)
}

// -----------------------------------------------------------------------------
// Bodies
// -----------------------------------------------------------------------------

enum class PhysicsBodyType { STATIC, KINEMATIC, DYNAMIC }

enum class PhysicsSleepState { AWAKE, SLEEPING }

data class PhysicsBodyDef(
    val id: Int,
    var type: PhysicsBodyType = PhysicsBodyType.DYNAMIC,
    var position: PhysicsVec2 = PhysicsVec2(),
    var angle: Float = 0f,
    var linearVelocity: PhysicsVec2 = PhysicsVec2(),
    var angularVelocity: Float = 0f,
    var shape: PhysicsShape = PhysicsShape(PhysicsShapeType.BOX),
    var material: PhysicsMaterial = PhysicsMaterial(),
    var filter: PhysicsFilter = PhysicsFilter(),
    var sensor: Boolean = false,
    var gravityScale: Float = 1f,
    var linearDamping: Float = 0.02f,
    var angularDamping: Float = 0.02f,
    var fixedRotation: Boolean = false,
    var bullet: Boolean = false,
    var allowSleep: Boolean = true
)

class PhysicsBody internal constructor(def: PhysicsBodyDef) {
    val id: Int = def.id
    var type: PhysicsBodyType = def.type
    val position = def.position.copyOf()
    val linearVelocity = def.linearVelocity.copyOf()
    var angle = def.angle
    var angularVelocity = def.angularVelocity
    var shape = def.shape.normalized()
    var material = def.material.sanitized()
    var filter = def.filter
    var sensor = def.sensor
    var gravityScale = def.gravityScale
    var linearDamping = def.linearDamping
    var angularDamping = def.angularDamping
    var fixedRotation = def.fixedRotation
    var bullet = def.bullet
    var allowSleep = def.allowSleep
    var sleepState = PhysicsSleepState.AWAKE
    var sleepTimer = 0f
    var force = PhysicsVec2()
    var torque = 0f
    var mass = 0f
    var inverseMass = 0f
    var inertia = 0f
    var inverseInertia = 0f
    var grounded = false
    var touching = false
    var userData: Any? = null

    init { resetMassFromShape() }

    fun resetMassFromShape() {
        if (type != PhysicsBodyType.DYNAMIC) {
            mass = 0f; inverseMass = 0f; inertia = 0f; inverseInertia = 0f
            return
        }
        val density = material.density.coerceAtLeast(0.001f)
        when (shape.type) {
            PhysicsShapeType.BOX -> {
                val w = shape.halfWidth * 2f; val h = shape.halfHeight * 2f
                mass = density * w * h
                inertia = mass * (w * w + h * h) / 12f
            }
            PhysicsShapeType.CIRCLE -> {
                mass = density * Math.PI.toFloat() * shape.radius * shape.radius
                inertia = 0.5f * mass * shape.radius * shape.radius
            }
            PhysicsShapeType.CAPSULE -> {
                val r = shape.radius; val length = shape.capsuleHalfLength * 2f
                val rectangle = density * length * 2f * r
                val circles = density * Math.PI.toFloat() * r * r
                mass = rectangle + circles
                inertia = mass * (length * length + 4f * r * r) / 12f
            }
        }
        inverseMass = if (mass > 0f) 1f / mass else 0f
        inverseInertia = if (!fixedRotation && inertia > 0f) 1f / inertia else 0f
    }

    fun applyForce(forceX: Float, forceY: Float) {
        if (type != PhysicsBodyType.DYNAMIC) return
        force.x += forceX; force.y += forceY; wake()
    }
    fun applyImpulse(ix: Float, iy: Float) {
        if (type != PhysicsBodyType.DYNAMIC) return
        linearVelocity.x += ix * inverseMass; linearVelocity.y += iy * inverseMass; wake()
    }
    fun applyTorque(value: Float) { if (type == PhysicsBodyType.DYNAMIC) { torque += value; wake() } }
    fun wake() { sleepState = PhysicsSleepState.AWAKE; sleepTimer = 0f }
    fun sleep() { sleepState = PhysicsSleepState.SLEEPING; linearVelocity.set(0f, 0f); angularVelocity = 0f; sleepTimer = 0f }
    fun isDynamic(): Boolean = type == PhysicsBodyType.DYNAMIC
}

// -----------------------------------------------------------------------------
// Contacts
// -----------------------------------------------------------------------------

data class PhysicsContact(
    val bodyA: Int,
    val bodyB: Int,
    val normalX: Float,
    val normalY: Float,
    val penetration: Float,
    val pointX: Float,
    val pointY: Float,
    val sensor: Boolean,
    val relativeNormalSpeed: Float
)

data class ContactPair(val a: Int, val b: Int) {
    companion object { fun of(a: Int, b: Int): ContactPair = if (a < b) ContactPair(a, b) else ContactPair(b, a) }
}

private data class Manifold(
    val normal: PhysicsVec2,
    val penetration: Float,
    val point: PhysicsVec2
)

// -----------------------------------------------------------------------------
// Spatial hash broad phase
// -----------------------------------------------------------------------------

class PhysicsSpatialHash(private val cellSize: Float = 128f) {
    private val cells = HashMap<Long, MutableList<Int>>()
    private val membership = HashMap<Int, LongArray>()

    private fun key(x: Int, y: Int): Long = (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)
    private fun cell(v: Float): Int = kotlin.math.floor(v / cellSize).toInt()

    fun clear() { cells.clear(); membership.clear() }

    fun insert(id: Int, aabb: PhysicsAabb) {
        val x0 = cell(aabb.minX); val x1 = cell(aabb.maxX)
        val y0 = cell(aabb.minY); val y1 = cell(aabb.maxY)
        val keys = ArrayList<Long>((x1 - x0 + 1) * (y1 - y0 + 1))
        for (y in y0..y1) for (x in x0..x1) {
            val k = key(x, y); keys += k; cells.getOrPut(k) { mutableListOf() }.add(id)
        }
        membership[id] = keys.toLongArray()
    }

    fun remove(id: Int) {
        membership.remove(id)?.forEach { k -> cells[k]?.remove(id); if (cells[k].isNullOrEmpty()) cells.remove(k) }
    }

    fun potentialPairs(): Sequence<ContactPair> = sequence {
        val seen = HashSet<Long>()
        for (ids in cells.values) {
            for (i in ids.indices) for (j in i + 1 until ids.size) {
                val a = min(ids[i], ids[j]); val b = max(ids[i], ids[j])
                val k = (a.toLong() shl 32) xor (b.toLong() and 0xffffffffL)
                if (seen.add(k)) yield(ContactPair(a, b))
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Joints / constraints
// -----------------------------------------------------------------------------

enum class PhysicsJointType { DISTANCE, SPRING, FIXED }

data class PhysicsJointDef(
    val id: Int,
    val type: PhysicsJointType,
    val bodyA: Int,
    val bodyB: Int,
    var anchorAX: Float = 0f,
    var anchorAY: Float = 0f,
    var anchorBX: Float = 0f,
    var anchorBY: Float = 0f,
    var length: Float = 1f,
    var stiffness: Float = 40f,
    var damping: Float = 4f,
    var enabled: Boolean = true
)

class PhysicsJoint internal constructor(def: PhysicsJointDef) {
    val id = def.id
    val type = def.type
    val bodyA = def.bodyA
    val bodyB = def.bodyB
    var anchorAX = def.anchorAX
    var anchorAY = def.anchorAY
    var anchorBX = def.anchorBX
    var anchorBY = def.anchorBY
    var length = def.length
    var stiffness = def.stiffness
    var damping = def.damping
    var enabled = def.enabled
}

// -----------------------------------------------------------------------------
// World
// -----------------------------------------------------------------------------

class PhysicsWorldV2(
    var fixedStep: Float = 1f / 60f,
    var maxSubSteps: Int = 8,
    var velocityIterations: Int = 8,
    var positionIterations: Int = 3,
    var spatialCellSize: Float = 128f
) {
    val gravity = PhysicsVec2(0f, 1600f)
    var timeScale = 1f
    var paused = false
    var continuousCollision = true
    var sleepLinearThreshold = 5f
    var sleepAngularThreshold = 0.05f
    var sleepTime = 0.5f

    private val bodies = LinkedHashMap<Int, PhysicsBody>()
    private val joints = LinkedHashMap<Int, PhysicsJoint>()
    private val broadPhase = PhysicsSpatialHash(spatialCellSize)
    private val contacts = ArrayList<PhysicsContact>()
    private val previousPairs = HashSet<ContactPair>()
    private val currentPairs = HashSet<ContactPair>()
    private val beginListeners = ArrayList<(PhysicsContact) -> Unit>()
    private val stayListeners = ArrayList<(PhysicsContact) -> Unit>()
    private val endListeners = ArrayList<(ContactPair) -> Unit>()
    private var accumulator = 0f
    var simulationTime = 0f
        private set
    var stepCount = 0L
        private set

    fun createBody(def: PhysicsBodyDef): PhysicsBody {
        require(!bodies.containsKey(def.id)) { "Physics body id already exists: ${def.id}" }
        val body = PhysicsBody(def); bodies[body.id] = body; return body
    }

    fun destroyBody(id: Int) {
        bodies.remove(id) ?: return
        joints.values.removeAll { it.bodyA == id || it.bodyB == id }
        previousPairs.removeIf { it.a == id || it.b == id }
        currentPairs.removeIf { it.a == id || it.b == id }
    }

    fun createJoint(def: PhysicsJointDef): PhysicsJoint {
        require(!joints.containsKey(def.id)) { "Physics joint id already exists: ${def.id}" }
        val joint = PhysicsJoint(def); joints[joint.id] = joint; return joint
    }

    fun destroyJoint(id: Int) { joints.remove(id) }
    fun body(id: Int): PhysicsBody? = bodies[id]
    fun joint(id: Int): PhysicsJoint? = joints[id]
    fun allBodies(): List<PhysicsBody> = bodies.values.toList()
    fun allJoints(): List<PhysicsJoint> = joints.values.toList()
    fun contacts(): List<PhysicsContact> = contacts.toList()

    fun onContactBegin(listener: (PhysicsContact) -> Unit) { beginListeners += listener }
    fun onContactStay(listener: (PhysicsContact) -> Unit) { stayListeners += listener }
    fun onContactEnd(listener: (ContactPair) -> Unit) { endListeners += listener }

    fun clearListeners() { beginListeners.clear(); stayListeners.clear(); endListeners.clear() }

    fun step(frameDelta: Float) {
        if (paused) return
        val dt = frameDelta.coerceAtLeast(0f).coerceAtMost(0.25f) * timeScale.coerceAtLeast(0f)
        accumulator = min(accumulator + dt, fixedStep * maxSubSteps)
        var subSteps = 0
        while (accumulator >= fixedStep && subSteps < maxSubSteps) {
            tick(fixedStep)
            accumulator -= fixedStep
            subSteps++
        }
        if (subSteps == maxSubSteps) accumulator = 0f
    }

    fun simulateFixed(steps: Int) {
        repeat(steps.coerceAtLeast(0)) { if (!paused) tick(fixedStep) }
    }

    private fun tick(dt: Float) {
        previousPairs.clear(); previousPairs.addAll(currentPairs); currentPairs.clear(); contacts.clear()
        integrateForces(dt)
        broadPhase.clear()
        bodies.values.forEach { body -> broadPhase.insert(body.id, computeAabb(body).expanded(if (body.bullet) 2f else 0.5f)) }
        val pairs = broadPhase.potentialPairs()
        pairs.forEach { pair ->
            val a = bodies[pair.a] ?: return@forEach
            val b = bodies[pair.b] ?: return@forEach
            if (!shouldProcess(a, b)) return@forEach
            val manifold = collide(a, b) ?: return@forEach
            val relative = PhysicsVec2(b.linearVelocity.x - a.linearVelocity.x, b.linearVelocity.y - a.linearVelocity.y)
            val contact = PhysicsContact(a.id, b.id, manifold.normal.x, manifold.normal.y, manifold.penetration, manifold.point.x, manifold.point.y, a.sensor || b.sensor, dot(relative, manifold.normal))
            contacts += contact; currentPairs += pair
            if (!contact.sensor) {
                repeat(velocityIterations) { solveVelocity(a, b, manifold) }
            }
        }
        repeat(positionIterations) { solvePositions() }
        solveJoints(dt)
        integratePositions(dt)
        updateSleep(dt)
        emitEvents()
        simulationTime += dt
        stepCount++
    }

    private fun shouldProcess(a: PhysicsBody, b: PhysicsBody): Boolean {
        if (a.type == PhysicsBodyType.STATIC && b.type == PhysicsBodyType.STATIC) return false
        return a.filter.canCollide(b.filter)
    }

    private fun integrateForces(dt: Float) {
        bodies.values.forEach { b ->
            if (!b.isDynamic() || b.sleepState == PhysicsSleepState.SLEEPING) return@forEach
            b.linearVelocity.x += (gravity.x * b.gravityScale + b.force.x * b.inverseMass) * dt
            b.linearVelocity.y += (gravity.y * b.gravityScale + b.force.y * b.inverseMass) * dt
            if (!b.fixedRotation) b.angularVelocity += b.torque * b.inverseInertia * dt
            val ld = (1f - b.linearDamping * dt).coerceAtLeast(0f)
            val ad = (1f - b.angularDamping * dt).coerceAtLeast(0f)
            b.linearVelocity.mul(ld); b.angularVelocity *= ad
            b.force.set(0f, 0f); b.torque = 0f
        }
    }

    private fun integratePositions(dt: Float) {
        bodies.values.forEach { b ->
            if (b.type == PhysicsBodyType.STATIC || b.sleepState == PhysicsSleepState.SLEEPING) return@forEach
            b.position.x += b.linearVelocity.x * dt
            b.position.y += b.linearVelocity.y * dt
            if (!b.fixedRotation) b.angle += b.angularVelocity * dt
        }
    }

    private fun computeAabb(b: PhysicsBody): PhysicsAabb {
        return when (b.shape.type) {
            PhysicsShapeType.CIRCLE -> PhysicsAabb(b.position.x - b.shape.radius, b.position.y - b.shape.radius, b.position.x + b.shape.radius, b.position.y + b.shape.radius)
            PhysicsShapeType.CAPSULE -> {
                val axis = rotate(PhysicsVec2(b.shape.capsuleHalfLength, 0f), b.angle)
                val rx = abs(axis.x) + b.shape.radius; val ry = abs(axis.y) + b.shape.radius
                PhysicsAabb(b.position.x - rx, b.position.y - ry, b.position.x + rx, b.position.y + ry)
            }
            PhysicsShapeType.BOX -> {
                val ex = abs(cos(b.angle)) * b.shape.halfWidth + abs(sin(b.angle)) * b.shape.halfHeight
                val ey = abs(sin(b.angle)) * b.shape.halfWidth + abs(cos(b.angle)) * b.shape.halfHeight
                PhysicsAabb(b.position.x - ex, b.position.y - ey, b.position.x + ex, b.position.y + ey)
            }
        }
    }

    private fun collide(a: PhysicsBody, b: PhysicsBody): Manifold? {
        return when {
            a.shape.type == PhysicsShapeType.CIRCLE && b.shape.type == PhysicsShapeType.CIRCLE -> circleCircle(a, b)
            a.shape.type == PhysicsShapeType.BOX && b.shape.type == PhysicsShapeType.BOX -> boxBox(a, b)
            a.shape.type == PhysicsShapeType.CIRCLE && b.shape.type == PhysicsShapeType.BOX -> circleBox(a, b, false)
            a.shape.type == PhysicsShapeType.BOX && b.shape.type == PhysicsShapeType.CIRCLE -> circleBox(b, a, true)
            else -> aabbFallback(a, b)
        }
    }

    private fun circleCircle(a: PhysicsBody, b: PhysicsBody): Manifold? {
        val d = PhysicsVec2(b.position.x - a.position.x, b.position.y - a.position.y)
        val distSq = d.lengthSquared(); val radius = a.shape.radius + b.shape.radius
        if (distSq > radius * radius) return null
        val dist = sqrt(max(distSq, 1e-12f))
        if (dist > 1e-5f) d.mul(1f / dist) else d.set(1f, 0f)
        return Manifold(d, radius - dist, PhysicsVec2(a.position.x + d.x * a.shape.radius, a.position.y + d.y * a.shape.radius))
    }

    private fun boxBox(a: PhysicsBody, b: PhysicsBody): Manifold? {
        val aa = computeAabb(a); val bb = computeAabb(b)
        if (!aa.overlaps(bb)) return null
        val px = min(aa.maxX - bb.minX, bb.maxX - aa.minX)
        val py = min(aa.maxY - bb.minY, bb.maxY - aa.minY)
        if (px < py) {
            val nx = if (b.position.x >= a.position.x) 1f else -1f
            return Manifold(PhysicsVec2(nx, 0f), px, PhysicsVec2((a.position.x + b.position.x) * 0.5f, (a.position.y + b.position.y) * 0.5f))
        }
        val ny = if (b.position.y >= a.position.y) 1f else -1f
        return Manifold(PhysicsVec2(0f, ny), py, PhysicsVec2((a.position.x + b.position.x) * 0.5f, (a.position.y + b.position.y) * 0.5f))
    }

    private fun circleBox(circle: PhysicsBody, box: PhysicsBody, reversed: Boolean): Manifold? {
        val rel = PhysicsVec2(circle.position.x - box.position.x, circle.position.y - box.position.y)
        val local = rotate(rel, -box.angle)
        val closest = PhysicsVec2(clamp(local.x, -box.shape.halfWidth, box.shape.halfWidth), clamp(local.y, -box.shape.halfHeight, box.shape.halfHeight))
        var delta = PhysicsVec2(local.x - closest.x, local.y - closest.y)
        val distSq = delta.lengthSquared()
        if (distSq > circle.shape.radius * circle.shape.radius) return null
        if (distSq < 1e-10f) {
            val dx = box.shape.halfWidth - abs(local.x); val dy = box.shape.halfHeight - abs(local.y)
            delta = if (dx < dy) PhysicsVec2(if (local.x >= 0f) 1f else -1f, 0f) else PhysicsVec2(0f, if (local.y >= 0f) 1f else -1f)
            val normal = rotate(delta, box.angle)
            val n = if (reversed) PhysicsVec2(-normal.x, -normal.y) else normal
            return Manifold(n, circle.shape.radius + min(dx, dy), PhysicsVec2(circle.position.x, circle.position.y))
        }
        val dist = sqrt(distSq); delta.mul(1f / dist)
        var normal = rotate(delta, box.angle)
        if (reversed) normal.mul(-1f)
        val pointLocal = closest
        val pointWorld = PhysicsVec2(box.position.x + rotate(pointLocal, box.angle).x, box.position.y + rotate(pointLocal, box.angle).y)
        return Manifold(normal, circle.shape.radius - dist, pointWorld)
    }

    private fun aabbFallback(a: PhysicsBody, b: PhysicsBody): Manifold? {
        val aa = computeAabb(a); val bb = computeAabb(b)
        if (!aa.overlaps(bb)) return null
        val px = min(aa.maxX - bb.minX, bb.maxX - aa.minX)
        val py = min(aa.maxY - bb.minY, bb.maxY - aa.minY)
        return if (px < py) Manifold(PhysicsVec2(if (b.position.x >= a.position.x) 1f else -1f, 0f), px, PhysicsVec2((a.position.x + b.position.x) * .5f, (a.position.y + b.position.y) * .5f))
        else Manifold(PhysicsVec2(0f, if (b.position.y >= a.position.y) 1f else -1f), py, PhysicsVec2((a.position.x + b.position.x) * .5f, (a.position.y + b.position.y) * .5f))
    }

    private fun solveVelocity(a: PhysicsBody, b: PhysicsBody, m: Manifold) {
        val invMass = a.inverseMass + b.inverseMass
        if (invMass <= 0f) return
        val rv = PhysicsVec2(b.linearVelocity.x - a.linearVelocity.x, b.linearVelocity.y - a.linearVelocity.y)
        val vn = dot(rv, m.normal)
        if (vn > 0f) return
        val restitution = min(a.material.restitution, b.material.restitution)
        val impulseMag = -(1f + restitution) * vn / invMass
        val impulse = PhysicsVec2(m.normal.x * impulseMag, m.normal.y * impulseMag)
        if (a.isDynamic()) { a.linearVelocity.x -= impulse.x * a.inverseMass; a.linearVelocity.y -= impulse.y * a.inverseMass; a.wake() }
        if (b.isDynamic()) { b.linearVelocity.x += impulse.x * b.inverseMass; b.linearVelocity.y += impulse.y * b.inverseMass; b.wake() }
        val tangent = PhysicsVec2(rv.x - vn * m.normal.x, rv.y - vn * m.normal.y)
        val tl = tangent.length()
        if (tl <= 1e-5f) return
        tangent.mul(1f / tl)
        val jt = -dot(rv, tangent) / invMass
        val friction = sqrt(a.material.friction * b.material.friction)
        val maxFriction = impulseMag * friction
        val frictionImpulse = clamp(jt, -maxFriction, maxFriction)
        val fi = PhysicsVec2(tangent.x * frictionImpulse, tangent.y * frictionImpulse)
        if (a.isDynamic()) { a.linearVelocity.x -= fi.x * a.inverseMass; a.linearVelocity.y -= fi.y * a.inverseMass }
        if (b.isDynamic()) { b.linearVelocity.x += fi.x * b.inverseMass; b.linearVelocity.y += fi.y * b.inverseMass }
    }

    private fun solvePositions() {
        contacts.forEach { c ->
            val a = bodies[c.bodyA] ?: return@forEach; val b = bodies[c.bodyB] ?: return@forEach
            if (c.sensor) return@forEach
            val total = a.inverseMass + b.inverseMass
            if (total <= 0f) return@forEach
            val correction = max(c.penetration - 0.01f, 0f) * 0.65f / total
            if (a.isDynamic()) { a.position.x -= c.normalX * correction * a.inverseMass; a.position.y -= c.normalY * correction * a.inverseMass }
            if (b.isDynamic()) { b.position.x += c.normalX * correction * b.inverseMass; b.position.y += c.normalY * correction * b.inverseMass }
        }
    }

    private fun solveJoints(dt: Float) {
        joints.values.filter { it.enabled }.forEach { joint ->
            val a = bodies[joint.bodyA] ?: return@forEach; val b = bodies[joint.bodyB] ?: return@forEach
            val pa = PhysicsVec2(a.position.x + joint.anchorAX, a.position.y + joint.anchorAY)
            val pb = PhysicsVec2(b.position.x + joint.anchorBX, b.position.y + joint.anchorBY)
            val d = PhysicsVec2(pb.x - pa.x, pb.y - pa.y); val len = d.length()
            if (len < 1e-5f) return@forEach
            d.mul(1f / len)
            when (joint.type) {
                PhysicsJointType.DISTANCE -> {
                    val error = len - joint.length
                    val impulse = error / (a.inverseMass + b.inverseMass).coerceAtLeast(1e-5f)
                    if (a.isDynamic()) { a.position.x += d.x * impulse * a.inverseMass * dt; a.position.y += d.y * impulse * a.inverseMass * dt }
                    if (b.isDynamic()) { b.position.x -= d.x * impulse * b.inverseMass * dt; b.position.y -= d.y * impulse * b.inverseMass * dt }
                }
                PhysicsJointType.SPRING -> {
                    val error = len - joint.length
                    val relative = PhysicsVec2(b.linearVelocity.x - a.linearVelocity.x, b.linearVelocity.y - a.linearVelocity.y)
                    val forceValue = -joint.stiffness * error - joint.damping * dot(relative, d)
                    val fx = d.x * forceValue; val fy = d.y * forceValue
                    if (a.isDynamic()) a.applyForce(-fx, -fy)
                    if (b.isDynamic()) b.applyForce(fx, fy)
                }
                PhysicsJointType.FIXED -> {
                    val error = len
                    val impulse = error / (a.inverseMass + b.inverseMass).coerceAtLeast(1e-5f)
                    if (a.isDynamic()) { a.position.x += d.x * impulse * a.inverseMass; a.position.y += d.y * impulse * a.inverseMass }
                    if (b.isDynamic()) { b.position.x -= d.x * impulse * b.inverseMass; b.position.y -= d.y * impulse * b.inverseMass }
                }
            }
        }
    }

    private fun updateSleep(dt: Float) {
        bodies.values.forEach { b ->
            if (!b.isDynamic() || !b.allowSleep) return@forEach
            val slow = b.linearVelocity.length() <= sleepLinearThreshold && abs(b.angularVelocity) <= sleepAngularThreshold
            if (slow) {
                b.sleepTimer += dt
                if (b.sleepTimer >= sleepTime) b.sleep()
            } else b.sleepTimer = 0f
        }
    }

    private fun emitEvents() {
        currentPairs.forEach { pair ->
            val c = contacts.firstOrNull { it.bodyA == pair.a && it.bodyB == pair.b || it.bodyA == pair.b && it.bodyB == pair.a } ?: return@forEach
            if (previousPairs.contains(pair)) stayListeners.toList().forEach { it(c) }
            else beginListeners.toList().forEach { it(c) }
        }
        previousPairs.filter { !currentPairs.contains(it) }.forEach { pair -> endListeners.toList().forEach { it(pair) } }
    }

    fun queryAabb(aabb: PhysicsAabb, includeSensors: Boolean = true): List<PhysicsBody> =
        bodies.values.filter { (includeSensors || !it.sensor) && computeAabb(it).overlaps(aabb) }

    fun rayCast(originX: Float, originY: Float, dirX: Float, dirY: Float, maxDistance: Float = 1000f): PhysicsRayHit? {
        val direction = PhysicsVec2(dirX, dirY); val len = direction.length()
        if (len < 1e-6f) return null
        direction.mul(1f / len)
        var best: PhysicsRayHit? = null
        bodies.values.forEach { body ->
            val a = computeAabb(body)
            val t = rayAabb(originX, originY, direction.x, direction.y, a) ?: return@forEach
            if (t <= maxDistance && (best == null || t < best!!.distance)) {
                best = PhysicsRayHit(body.id, originX + direction.x * t, originY + direction.y * t, t)
            }
        }
        return best
    }

    private fun rayAabb(ox: Float, oy: Float, dx: Float, dy: Float, a: PhysicsAabb): Float? {
        var tmin = -Float.MAX_VALUE; var tmax = Float.MAX_VALUE
        if (abs(dx) < 1e-8f) { if (ox < a.minX || ox > a.maxX) return null }
        else { val inv = 1f / dx; var t1 = (a.minX - ox) * inv; var t2 = (a.maxX - ox) * inv; if (t1 > t2) { val q=t1;t1=t2;t2=q }; tmin=max(tmin,t1);tmax=min(tmax,t2) }
        if (abs(dy) < 1e-8f) { if (oy < a.minY || oy > a.maxY) return null }
        else { val inv = 1f / dy; var t1 = (a.minY - oy) * inv; var t2 = (a.maxY - oy) * inv; if (t1 > t2) { val q=t1;t1=t2;t2=q }; tmin=max(tmin,t1);tmax=min(tmax,t2) }
        if (tmax < max(tmin, 0f)) return null
        return max(tmin, 0f)
    }

    fun debugSnapshot(): PhysicsDebugSnapshot = PhysicsDebugSnapshot(
        bodies = bodies.size,
        dynamicBodies = bodies.values.count { it.isDynamic() },
        contacts = contacts.size,
        joints = joints.size,
        sleepingBodies = bodies.values.count { it.sleepState == PhysicsSleepState.SLEEPING },
        simulationTime = simulationTime,
        stepCount = stepCount
    )
}

data class PhysicsRayHit(val bodyId: Int, val x: Float, val y: Float, val distance: Float)
data class PhysicsDebugSnapshot(val bodies: Int, val dynamicBodies: Int, val contacts: Int, val joints: Int, val sleepingBodies: Int, val simulationTime: Float, val stepCount: Long)
