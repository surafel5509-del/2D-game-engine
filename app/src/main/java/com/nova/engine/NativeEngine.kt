package com.nova.engine

class NativeEngine {
    companion object { init { System.loadLibrary("nova_engine") } }
    external fun init()
    external fun createEntity(x: Float, y: Float): Int
    external fun setVelocity(id: Int, vx: Float, vy: Float)
    external fun setBody(id: Int, dynamic: Boolean, sensor: Boolean)
    external fun setSize(id: Int, width: Float, height: Float)
    external fun setCollisionFilter(id: Int, layer: Int, mask: Int)
    external fun applyImpulse(id: Int, ix: Float, iy: Float)
    external fun destroyEntity(id: Int)
    external fun step(dt: Float)
    external fun getTransforms(): FloatArray
    external fun clear()

    external fun initializeRenderer(width: Int, height: Int): Boolean
    external fun resizeRenderer(width: Int, height: Int)
    external fun beginRenderer(r: Float = .08f, g: Float = .09f, b: Float = .12f, a: Float = 1f)
    external fun submitRendererSprite(texture: Int, x: Float, y: Float, width: Float, height: Float, rotation: Float = 0f, pivotX: Float = .5f, pivotY: Float = .5f, u0: Float = 0f, v0: Float = 0f, u1: Float = 1f, v1: Float = 1f, r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f)
    external fun endRenderer()
    external fun rendererStats(): LongArray?
    external fun shutdownRenderer()
}
