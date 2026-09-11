package com.nova.engine

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class Particle2D(var x:Float,var y:Float,var vx:Float,var vy:Float,var life:Float,var size:Float,var rotation:Float=0f)

class ParticleEmitter2D(private val maxParticles:Int=512) {
    val particles = ArrayList<Particle2D>(maxParticles)
    var emissionRate = 60f
    var lifetime = 1.2f
    var speed = 120f
    var gravity = 180f
    var spreadRadians = Math.PI.toFloat()
    var enabled = true
    private var carry = 0f

    fun update(dt:Float, originX:Float, originY:Float) {
        if(enabled){carry += emissionRate*dt; while(carry>=1f){if(particles.size<maxParticles) emit(originX,originY);carry-=1f}}
        val it=particles.iterator(); while(it.hasNext()){val p=it.next();p.life-=dt;if(p.life<=0f){it.remove();continue};p.vy+=gravity*dt;p.x+=p.vx*dt;p.y+=p.vy*dt;p.rotation+=dt*2f}
    }
    fun burst(count:Int,x:Float,y:Float){repeat(count.coerceAtMost(maxParticles-particles.size)){emit(x,y)}}
    private fun emit(x:Float,y:Float){val a=(-spreadRadians/2f..spreadRadians/2f).random();val s=speed*(0.65f+Random.nextFloat()*0.7f);particles+=Particle2D(x,y,cos(a)*s,sin(a)*s,lifetime*(0.6f+Random.nextFloat()*0.8f),4f+Random.nextFloat()*8f)}
}
