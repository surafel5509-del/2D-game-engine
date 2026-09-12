package com.nova.engine

/** Lightweight gameplay scripting contract. Games can implement this without depending on editor classes. */
interface NovaScript {
    fun onCreate(ctx: ScriptContext) {}
    fun onUpdate(ctx: ScriptContext, deltaSeconds: Float) {}
    fun onDestroy(ctx: ScriptContext) {}
    fun onCollision(ctx: ScriptContext, otherEntityId: Int) {}
}

class ScriptContext internal constructor(private val world: ScriptWorld, val entityId: Int) {
    fun position(): Pair<Float, Float> = world.position(entityId)
    fun setPosition(x: Float, y: Float) = world.setPosition(entityId, x, y)
    fun velocity(): Pair<Float, Float> = world.velocity(entityId)
    fun setVelocity(x: Float, y: Float) = world.setVelocity(entityId, x, y)
    fun spawn(prefabId: String, x: Float, y: Float): Int = world.spawn(prefabId, x, y)
    fun destroy() = world.destroy(entityId)
    fun log(message: String) = world.log("Entity[$entityId] $message")
}

interface ScriptWorld {
    fun position(entityId: Int): Pair<Float, Float>
    fun setPosition(entityId: Int, x: Float, y: Float)
    fun velocity(entityId: Int): Pair<Float, Float>
    fun setVelocity(entityId: Int, x: Float, y: Float)
    fun spawn(prefabId: String, x: Float, y: Float): Int
    fun destroy(entityId: Int)
    fun log(message: String)
}

class ScriptRunner(private val world: ScriptWorld) {
    private val scripts = linkedMapOf<Int, NovaScript>()
    fun attach(entityId: Int, script: NovaScript) { scripts[entityId] = script; script.onCreate(ScriptContext(world, entityId)) }
    fun detach(entityId: Int) { scripts.remove(entityId)?.onDestroy(ScriptContext(world, entityId)) }
    fun update(delta: Float) { scripts.toList().forEach { (id, script) -> if (scripts[id] === script) script.onUpdate(ScriptContext(world, id), delta) } }
    fun collision(entityId: Int, other: Int) { scripts[entityId]?.onCollision(ScriptContext(world, entityId), other) }
    fun clear() { scripts.keys.toList().forEach(::detach) }
}
