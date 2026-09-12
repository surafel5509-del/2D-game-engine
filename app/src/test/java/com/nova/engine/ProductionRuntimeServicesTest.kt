package com.nova.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionRuntimeServicesTest {
    @Test fun animationLoopsAndAdvances() {
        val player = SpriteAnimationPlayer()
        val clip = AnimationClip("Run", mutableListOf(
            AnimationFrame("a", 100), AnimationFrame("b", 100)
        ), loop = true, speed = 1f)
        player.play(clip)
        assertEquals("a", player.update(0f))
        assertEquals("b", player.update(100f))
        assertEquals("a", player.update(100f))
        assertTrue(player.playing)
    }

    @Test fun nonLoopingAnimationStopsAtLastFrame() {
        val player = SpriteAnimationPlayer()
        val clip = AnimationClip("OneShot", mutableListOf(
            AnimationFrame("a", 50), AnimationFrame("b", 50)
        ), loop = false)
        player.play(clip)
        player.update(100f)
        assertEquals("b", player.update(0f))
        assertFalse(player.playing)
    }

    @Test fun runtimeSessionUsesFixedStep() {
        val runtime = RuntimeSession()
        var steps = 0
        assertEquals(6, runtime.advance(.1f) { steps++ })
        assertEquals(6, steps)
        assertEquals(.1, runtime.simulationTime, .0001)
    }

    @Test fun scenePersistenceRoundTripsCoreFields() {
        val input = listOf(EditorEntity(7, "Hero", 10f, 20f, 64f, 32f, false, true, 3, "hero", "CharacterBody2D"))
        val decoded = ScenePersistence.decode(ScenePersistence.encode("TestScene", input))
        assertEquals("TestScene", decoded.first)
        assertEquals(1, decoded.second.size)
        assertEquals(input[0], decoded.second[0])
    }
}
