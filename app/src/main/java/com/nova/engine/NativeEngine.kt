package com.nova.engine

class NativeEngine {
    companion object { init { System.loadLibrary("nova_engine") } }
    external fun init(); external fun createEntity(x: Float, y: Float): Int
    external fun setVelocity(id: Int, vx: Float, vy: Float); external fun step(dt: Float)
    external fun getTransforms(): FloatArray; external fun clear()
}
