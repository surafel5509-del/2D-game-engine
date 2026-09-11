package com.nova.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

class GameView(context: Context) : View(context) {
    private val native = NativeEngine(); private val paint=Paint(Paint.ANTI_ALIAS_FLAG); private var last=System.nanoTime(); private var player=0
    init { native.init(); player=native.createEntity(160f,300f); setBackgroundColor(0xFF10151C.toInt()); isFocusable=true }
    override fun onDraw(canvas: Canvas) { val now=System.nanoTime(); val dt=max(0.0, (now-last)/1e9).toFloat(); last=now; native.step(dt); drawGrid(canvas); val t=native.getTransforms(); var i=0; while(i<t.size){ val x=t[i]; val y=t[i+1]; val w=t[i+2]; val h=t[i+3]; paint.setColor(if(i==0)0xFF66D9FF.toInt() else 0xFFFFC857.toInt()); canvas.drawRoundRect(RectF(x,y,x+w,y+h),10f,10f,paint); i+=4 }; postInvalidateOnAnimation() }
    private fun drawGrid(c:Canvas){ paint.setColor(0xFF202A35.toInt()); paint.strokeWidth=1f; var x=0f; while(x<width){c.drawLine(x,0f,x,height.toFloat(),paint);x+=64}; var y=0f; while(y<height){c.drawLine(0f,y,width.toFloat(),y,paint);y+=64} }
    override fun onTouchEvent(e:MotionEvent):Boolean { if(e.action==MotionEvent.ACTION_DOWN||e.action==MotionEvent.ACTION_MOVE){ val vx=(e.x-width/2f)*2f; native.setVelocity(player,vx.coerceIn(-500f,500f),-700f); return true }; return true }
}
