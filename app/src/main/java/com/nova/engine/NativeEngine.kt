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
}
