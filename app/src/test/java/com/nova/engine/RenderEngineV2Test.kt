package com.nova.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderEngineV2Test {
    @Test fun cameraRoundTripPreservesWorldPoint() {
        val camera = RenderCamera2D()
        camera.resize(1000, 600)
        camera.position.set(100f, 50f)
        camera.setZoom(2f)
        val screen = camera.worldToScreen(130f, 80f)
        val world = camera.screenToWorld(screen.x, screen.y)
        assertEquals(130f, world.x, 0.001f)
        assertEquals(80f, world.y, 0.001f)
    }

    @Test fun queueCullsOutsideObjects() {
        val camera = RenderCamera2D()
        camera.resize(100, 100)
        val queue = RenderQueue()
        queue.add(RenderCommand(1, null, "sprite", RenderTransform(0f,0f), RenderSprite(width=10f,height=10f)))
        queue.add(RenderCommand(2, null, "sprite", RenderTransform(1000f,1000f), RenderSprite(width=10f,height=10f)))
        queue.cull(camera)
        assertEquals(1, queue.size())
        assertEquals(1, queue.culled)
    }

    @Test fun pickerReturnsTopmostVisibleCommand() {
        val camera = RenderCamera2D()
        camera.resize(200, 200)
        val queue = RenderQueue()
        queue.add(RenderCommand(1,null,"a",RenderTransform(0f,0f),RenderSprite(width=50f,height=50f),zIndex=0))
        queue.add(RenderCommand(2,null,"b",RenderTransform(0f,0f),RenderSprite(width=30f,height=30f),zIndex=1))
        val picked = RenderPicker(camera).pick(queue.all(),100f,100f)
        assertNotNull(picked)
        assertEquals(2, picked?.entityId)
    }

    @Test fun viewportZoomAtKeepsWorldAnchor() {
        val camera = RenderCamera2D()
        camera.resize(400,400)
        val tools = RenderViewportTools(camera)
        val before = camera.screenToWorld(120f,160f)
        tools.zoomAt(120f,160f,2f)
        val after = camera.screenToWorld(120f,160f)
        assertEquals(before.x, after.x, 0.001f)
        assertEquals(before.y, after.y, 0.001f)
    }
}
