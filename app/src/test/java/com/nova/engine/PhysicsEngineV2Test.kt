package com.nova.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsEngineV2Test {
    @Test fun dynamicBodyFallsAndStopsOnStaticFloor() {
        val world = PhysicsWorldV2()
        world.gravity.set(0f, 100f)
        world.createBody(PhysicsBodyDef(1, PhysicsBodyType.STATIC, PhysicsVec2(0f, 100f), shape = PhysicsShape(PhysicsShapeType.BOX, 100f, 10f)))
        val player = world.createBody(PhysicsBodyDef(2, PhysicsBodyType.DYNAMIC, PhysicsVec2(0f, 0f), shape = PhysicsShape(PhysicsShapeType.BOX, 5f, 5f)))
        world.simulateFixed(120)
        assertTrue(player.position.y < 100f)
        assertTrue(player.grounded || world.contacts().isNotEmpty())
        assertEquals(0f, player.linearVelocity.y, 0.001f)
    }

    @Test fun sensorProducesContactWithoutResolution() {
        val world = PhysicsWorldV2()
        world.gravity.set(0f, 0f)
        world.createBody(PhysicsBodyDef(1, PhysicsBodyType.STATIC, PhysicsVec2(0f, 0f), shape = PhysicsShape(PhysicsShapeType.BOX, 10f, 10f), sensor = true))
        val body = world.createBody(PhysicsBodyDef(2, PhysicsBodyType.DYNAMIC, PhysicsVec2(0f, 0f), shape = PhysicsShape(PhysicsShapeType.BOX, 1f, 1f)))
        world.simulateFixed(1)
        assertTrue(world.contacts().any { it.sensor })
        assertEquals(0f, body.position.x, 0.001f)
    }

    @Test fun rayCastReturnsNearestBody() {
        val world = PhysicsWorldV2()
        world.gravity.set(0f, 0f)
        world.createBody(PhysicsBodyDef(1, PhysicsBodyType.STATIC, PhysicsVec2(10f, 0f), shape = PhysicsShape(PhysicsShapeType.BOX, 1f, 1f)))
        world.createBody(PhysicsBodyDef(2, PhysicsBodyType.STATIC, PhysicsVec2(20f, 0f), shape = PhysicsShape(PhysicsShapeType.BOX, 1f, 1f)))
        val hit = world.rayCast(0f, 0f, 1f, 0f, 100f)
        assertEquals(1, hit?.bodyId)
        assertTrue((hit?.distance ?: 999f) < 20f)
    }
}
