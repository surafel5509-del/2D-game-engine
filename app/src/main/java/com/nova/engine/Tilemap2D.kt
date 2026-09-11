package com.nova.engine

/** Compact chunk-friendly tilemap data model for large 2D worlds. */
class Tilemap2D(val width: Int, val height: Int, val tileSize: Int = 32, val layers: Int = 1) {
    private val data = IntArray(width * height * layers) { -1 }
    fun get(x: Int, y: Int, layer: Int = 0): Int = if (x in 0 until width && y in 0 until height && layer in 0 until layers) data[index(x,y,layer)] else -1
    fun set(x: Int, y: Int, tile: Int, layer: Int = 0) { if (x in 0 until width && y in 0 until height && layer in 0 until layers) data[index(x,y,layer)] = tile }
    fun fill(tile: Int, layer: Int = 0) { for (y in 0 until height) for (x in 0 until width) set(x,y,tile,layer) }
    fun clear(layer: Int = 0) = fill(-1, layer)
    fun worldToTile(worldX: Float, worldY: Float): Pair<Int,Int> = Pair((worldX/tileSize).toInt(), (worldY/tileSize).toInt())
    fun tileToWorld(x: Int, y: Int): Pair<Float,Float> = Pair(x*tileSize.toFloat(), y*tileSize.toFloat())
    fun serialize(): String = buildString {
        appendLine("NOVA_TILEMAP 1|$width|$height|$tileSize|$layers")
        for (l in 0 until layers) appendLine(data.slice(l*width*height until (l+1)*width*height).joinToString(","))
    }
    private fun index(x:Int,y:Int,l:Int) = l*width*height+y*width+x
}

class TilemapPainter(private val map: Tilemap2D) {
    var selectedTile = 0
    var selectedLayer = 0
    fun paint(x:Int,y:Int) = map.set(x,y,selectedTile,selectedLayer)
    fun erase(x:Int,y:Int) = map.set(x,y,-1,selectedLayer)
    fun line(x0:Int,y0:Int,x1:Int,y1:Int) {
        var x=x0; var y=y0; val dx=kotlin.math.abs(x1-x0); val sx=if(x0<x1)1 else -1; val dy=-kotlin.math.abs(y1-y0); val sy=if(y0<y1)1 else -1; var err=dx+dy
        while(true){paint(x,y);if(x==x1&&y==y1)break;val e2=2*err;if(e2>=dy){err+=dy;x+=sx};if(e2<=dx){err+=dx;y+=sy}}
    }
}
