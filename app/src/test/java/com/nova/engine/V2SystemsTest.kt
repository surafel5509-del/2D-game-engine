package com.nova.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V2SystemsTest {
    @Test fun sceneSnapshotRoundTrip() {
        val scene = V2Scene("Test")
        scene.add(V2Node(name = "Player", type = "CharacterBody2D", x = 12f, y = 24f, rotation = 15f, scaleX = 2f, scaleY = .5f, body = "DYNAMIC", collider = "BOX"))
        val restored = V2SnapshotCodec.decode(V2SnapshotCodec.encode(scene))
        assertEquals("Test", restored.name)
        assertEquals(1, restored.nodes.size)
        assertEquals(12f, restored.nodes[0].x, .001f)
        assertEquals(2f, restored.nodes[0].scaleX, .001f)
    }

    @Test fun scriptRuntimeExecutesDeterministically() {
        val state = V2ScriptRuntime.State()
        val result = V2ScriptRuntime().execute("set speed 10\nadd speed 5\nmul speed 2\nflag ready true\nprint ok", state)
        assertTrue(result.errors.isEmpty())
        assertEquals(30f, state.numbers["speed"]!!, .001f)
        assertEquals(true, state.flags["ready"])
        assertEquals(listOf("ok"), result.logs)
    }

    @Test fun tileMapBrushAndCollisionWork() {
        val map = V2TileMap(8, 8, 2)
        map.paint(0, 3, 3, 7, 1)
        map.setCollision(0, 3, 3, true)
        assertEquals(9, map.count(0))
        assertTrue(map.isSolid(0, 3, 3))
        map.erase(0, 3, 3, 1)
        assertEquals(0, map.count(0))
    }

    @Test fun animationMakerBuildsSheetFrames() {
        val clip = V2AnimationMaker().fromSheet("Run", V2SpriteSheet("player.png", 32, 32, 4, 2), 12f)
        assertEquals(8, clip.frames.size)
        assertEquals(12f, clip.fps, .001f)
    }
}
