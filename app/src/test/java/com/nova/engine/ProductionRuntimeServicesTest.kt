package com.nova.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionRuntimeServicesTest {
    @Test fun animationLoopsAndAdvances() {
        val player = AnimationPlayer().add(AnimationClip("Run", listOf("a", "b"), 10f, PlaybackMode.LOOP))
        player.play("Run", true)
        assertEquals("a", player.frame())
        player.update(.1f)
        assertEquals("b", player.frame())
        player.update(.1f)
        assertEquals("a", player.frame())
        assertTrue(player.playing)
    }

    @Test fun nonLoopingAnimationStopsAtLastFrame() {
        val player = AnimationPlayer().add(AnimationClip("OneShot", listOf("a", "b"), 10f, PlaybackMode.ONCE))
        player.play("OneShot", true)
        player.update(.2f)
        assertEquals("b", player.frame())
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
