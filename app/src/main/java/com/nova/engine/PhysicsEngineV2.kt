package com.nova.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Nova Physics V2 kernel.
 *
 * The legacy PhysicsWorld2D remains available for compatibility. This kernel is
 * the new authoring/runtime boundary: immutable definitions enter the world,
 * mutable state stays inside the simulation, and events/queries leave it.
 * Fixed-step simulation makes replay and editor/runtime parity deterministic.
 */

data class PhysicsV2Vec2(var x: Float = 0f, var y: Float = 0f) {
    fun set(nx: Float, ny: Float) { x = nx; y = ny }
    fun add(v: PhysicsV2Vec2) { x += v.x; y += v.y }
    fun sub(v: PhysicsV2Vec2) { x -= v.x; y -= v.y }
    fun scale(s: Float) { x *= s; y *= s }
    fun dot(v: PhysicsV2Vec2) = x * v.x + y * v.y
    fun lengthSquared() = x * x + y * y
    fun length() = sqrt(lengthSquared())
    fun normalized(): PhysicsV2Vec2 { val l=length(); return if(l>1e-6f) PhysicsV2Vec2(x/l,y/l) else PhysicsV2Vec2() }
    fun copy() = PhysicsV2Vec2(x,y)
}

data class PhysicsV2Aabb(var minX: Float,var minY: Float,var maxX: Float,var maxY: Float) {
    fun overlaps(o: PhysicsV2Aabb) = minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY
    fun contains(x:Float,y:Float)=x>=minX&&x<=maxX&&y>=minY&&y<=maxY
    fun expanded(v:Float)=PhysicsV2Aabb(minX-v,minY-v,maxX+v,maxY+v)
}

enum class PhysicsV2BodyType { STATIC, KINEMATIC, DYNAMIC }
enum class PhysicsV2ShapeType { BOX, CIRCLE, CAPSULE }
enum class PhysicsV2JointType { DISTANCE, SPRING, FIXED }

data class PhysicsV2Material(val density:Float=1f,val friction:Float=.7f,val restitution:Float=.05f) {
    fun clean()=copy(density.coerceAtLeast(.001f),friction.coerceIn(0f,1f),restitution.coerceIn(0f,1f))
}
data class PhysicsV2Filter(val categoryBits:Int=1,val maskBits:Int=-1,val groupIndex:Int=0) {
    fun allows(o:PhysicsV2Filter):Boolean { if(groupIndex!=0&&groupIndex==o.groupIndex)return groupIndex>0; return (maskBits and o.categoryBits)!=0&&(o.maskBits and categoryBits)!=0 }
}
data class PhysicsV2Shape(val type:PhysicsV2ShapeType,val width:Float=1f,val height:Float=1f,val radius:Float=.5f,val length:Float=1f) {
    fun clean()=copy(width=width.coerceAtLeast(.001f),height=height.coerceAtLeast(.001f),radius=radius.coerceAtLeast(.001f),length=length.coerceAtLeast(0f))
}
data class PhysicsV2BodyDef(
    val id:Int,
    val type:PhysicsV2BodyType=PhysicsV2BodyType.DYNAMIC,
    val x:Float=0f,val y:Float=0f,val angle:Float=0f,
    val vx:Float=0f,val vy:Float=0f,val angularVelocity:Float=0f,
    val shape:PhysicsV2Shape=PhysicsV2Shape(PhysicsV2ShapeType.BOX),
    val material:PhysicsV2Material=PhysicsV2Material(),val filter:PhysicsV2Filter=PhysicsV2Filter(),
    val sensor:Boolean=false,val gravityScale:Float=1f,val linearDamping:Float=.02f,val angularDamping:Float=.02f,
    val fixedRotation:Boolean=false,val bullet:Boolean=false,val allowSleep:Boolean=true
)
data class PhysicsV2JointDef(val id:Int,val type:PhysicsV2JointType,val bodyA:Int,val bodyB:Int,val length:Float=1f,val stiffness:Float=40f,val damping:Float=4f)
data class PhysicsV2Contact(val a:Int,val b:Int,val normalX:Float,val normalY:Float,val penetration:Float,val pointX:Float,val pointY:Float,val sensor:Boolean)
data class PhysicsV2RayHit(val bodyId:Int,val fraction:Float,val x:Float,val y:Float,val normalX:Float,val normalY:Float)
data class PhysicsV2Stats(val steps:Long,val bodies:Int,val activeBodies:Int,val pairs:Int,val contacts:Int,val begin:Int,val stay:Int,val end:Int)

private class V2Body(def:PhysicsV2BodyDef) {
    val id=def.id
    var type=def.type
    val position=PhysicsV2Vec2(def.x,def.y)
    val velocity=PhysicsV2Vec2(def.vx,def.vy)
    var angle=def.angle
    var angularVelocity=def.angularVelocity
    var shape=def.shape.clean()
    var material=def.material.clean()
    var filter=def.filter
    var sensor=def.sensor
    var gravityScale=def.gravityScale
    var linearDamping=def.linearDamping.coerceAtLeast(0f)
    var angularDamping=def.angularDamping.coerceAtLeast(0f)
    var fixedRotation=def.fixedRotation
    var bullet=def.bullet
    var allowSleep=def.allowSleep
    var awake=true
    var sleepTimer=0f
    var force=PhysicsV2Vec2()
    var torque=0f
    var mass=0f
    var invMass=0f
    var inertia=0f
    var invInertia=0f
    init { recalculateMass() }
    fun dynamic()=type==PhysicsV2BodyType.DYNAMIC
    fun recalculateMass(){
        if(!dynamic()){mass=0f;invMass=0f;inertia=0f;invInertia=0f;return}
        val d=material.density
        when(shape.type){
            PhysicsV2ShapeType.BOX->{mass=d*shape.width*shape.height;inertia=mass*(shape.width*shape.width+shape.height*shape.height)/12f}
            PhysicsV2ShapeType.CIRCLE->{mass=d*Math.PI.toFloat()*shape.radius*shape.radius;inertia=.5f*mass*shape.radius*shape.radius}
            PhysicsV2ShapeType.CAPSULE->{val r=shape.radius;val l=shape.length;mass=d*(l*2f*r+Math.PI.toFloat()*r*r);inertia=mass*(l*l+4f*r*r)/12f}
        }
        invMass=if(mass>0f)1f/mass else 0f;invInertia=if(!fixedRotation&&inertia>0f)1f/inertia else 0f
    }
    fun wake(){awake=true;sleepTimer=0f}
}
private class V2Joint(val def:PhysicsV2JointDef)

class PhysicsWorldV2(
    var fixedStep:Float=1f/60f,
    var velocityIterations:Int=8,
    var positionIterations:Int=3,
    var maxSubSteps:Int=8,
    var cellSize:Float=128f
) {
    val gravity=PhysicsV2Vec2(0f,1600f)
    var timeScale=1f
    var paused=false
    var continuous=true
    var allowSleeping=true
    var sleepLinearThreshold=5f
    var sleepAngularThreshold=.05f
    var sleepTime=.5f
    private val bodies=LinkedHashMap<Int,V2Body>()
    private val joints=LinkedHashMap<Int,V2Joint>()
    private val contacts=ArrayList<PhysicsV2Contact>()
    private val currentPairs=HashSet<Long>()
    private val previousPairs=HashSet<Long>()
    private var accumulator=0f
    private var beginCount=0
    private var stayCount=0
    private var endCount=0
    private val beginListeners=ArrayList<(PhysicsV2Contact)->Unit>()
    private val stayListeners=ArrayList<(PhysicsV2Contact)->Unit>()
    private val endListeners=ArrayList<(Int,Int)->Unit>()
    var simulationTime=0f;private set
    var stepCount=0L;private set

    fun createBody(def:PhysicsV2BodyDef):PhysicsV2BodyHandle { require(def.id !in bodies);bodies[def.id]=V2Body(def);return PhysicsV2BodyHandle(this,def.id) }
    fun destroyBody(id:Int){bodies.remove(id);joints.values.removeAll{it.def.bodyA==id||it.def.bodyB==id};currentPairs.removeIf{pairA(it)==id||pairB(it)==id};previousPairs.removeIf{pairA(it)==id||pairB(it)==id}}
    fun createJoint(def:PhysicsV2JointDef){require(def.bodyA in bodies&&def.bodyB in bodies);joints[def.id]=V2Joint(def)}
    fun destroyJoint(id:Int){joints.remove(id)}
    fun body(id:Int):PhysicsV2BodyHandle?=if(id in bodies)PhysicsV2BodyHandle(this,id)else null
    fun bodyIds():List<Int>=bodies.keys.toList()
    fun contacts():List<PhysicsV2Contact>=contacts.toList()
    fun onBegin(listener:(PhysicsV2Contact)->Unit){beginListeners+=listener}
    fun onStay(listener:(PhysicsV2Contact)->Unit){stayListeners+=listener}
    fun onEnd(listener:(Int,Int)->Unit){endListeners+=listener}

    fun step(frameDelta:Float){if(paused)return;val dt=(frameDelta.coerceIn(0f,.25f)*timeScale.coerceAtLeast(0f));accumulator=min(accumulator+dt,fixedStep*maxSubSteps);var n=0;while(accumulator>=fixedStep&&n<maxSubSteps){tick(fixedStep);accumulator-=fixedStep;n++};if(n==maxSubSteps)accumulator=0f}
    fun simulateFixed(steps:Int){repeat(steps.coerceAtLeast(0)){if(!paused)tick(fixedStep)}}
    private fun tick(dt:Float){
        previousPairs.clear();previousPairs.addAll(currentPairs);currentPairs.clear();contacts.clear()
        bodies.values.forEach{b->b.wakeIfMoving();b.grounded=false;if(b.dynamic()&&b.awake){b.velocity.x+=(gravity.x*b.gravityScale+b.force.x*b.invMass)*dt;b.velocity.y+=(gravity.y*b.gravityScale+b.force.y*b.invMass)*dt;b.velocity.x*=1f/(1f+b.linearDamping*dt);b.velocity.y*=1f/(1f+b.linearDamping*dt);if(!b.fixedRotation)b.angularVelocity*=1f/(1f+b.angularDamping*dt);b.position.x+=b.velocity.x*dt;b.position.y+=b.velocity.y*dt;b.angle+=b.angularVelocity*dt;b.force.set(0f,0f);b.torque=0f}}
        val list=bodies.values.toList();for(i in list.indices)for(j in i+1 until list.size){val a=list[i];val b=list[j];if(a.type==PhysicsV2BodyType.STATIC&&b.type==PhysicsV2BodyType.STATIC)continue;if(!a.filter.allows(b.filter))continue;val key=pair(a.id,b.id);val c=collide(a,b)?:continue;currentPairs+=key;contacts+=c;if(previousPairs.contains(key)){stayCount++;stayListeners.forEach{it(c)}}else{beginCount++;beginListeners.forEach{it(c)}};if(!c.sensor)resolve(a,b,c)}
        joints.values.filter{it.def.bodyA in bodies&&it.def.bodyB in bodies}.forEach{solveJoint(it,dt)}
        updateSleep(dt);for(k in previousPairs)if(k !in currentPairs){endCount++;endListeners.forEach{it(pairA(k),pairB(k))}}
        simulationTime+=dt;stepCount++
    }
    private fun V2Body.wakeIfMoving(){if(velocity.lengthSquared()>sleepLinearThreshold*sleepLinearThreshold||abs(angularVelocity)>sleepAngularThreshold)wake()}
    private fun updateSleep(dt:Float){if(!allowSleeping)return;bodies.values.forEach{b->if(!b.dynamic()||!b.allowSleep){return@forEach};if(b.velocity.lengthSquared()<sleepLinearThreshold*sleepLinearThreshold&&abs(b.angularVelocity)<sleepAngularThreshold){b.sleepTimer+=dt;if(b.sleepTimer>=sleepTime){b.awake=false;b.velocity.set(0f,0f);b.angularVelocity=0f}}else b.sleepTimer=0f}}

    private fun aabb(b:V2Body):PhysicsV2Aabb{val w=when(b.shape.type){PhysicsV2ShapeType.CIRCLE->b.shape.radius*2f;else->b.shape.width};val h=when(b.shape.type){PhysicsV2ShapeType.CIRCLE->b.shape.radius*2f;else->b.shape.height};val c=abs(cos(b.angle));val s=abs(sin(b.angle));val hw=(w*c+h*s)/2f;val hh=(w*s+h*c)/2f;return PhysicsV2Aabb(b.position.x-hw,b.position.y-hh,b.position.x+hw,b.position.y+hh)}
    private fun collide(a:V2Body,b:V2Body):PhysicsV2Contact?{val aa=aabb(a);val bb=aabb(b);if(!aa.overlaps(bb))return null;val dx=b.position.x-a.position.x;val dy=b.position.y-a.position.y;val px=min(aa.maxX-bb.minX,bb.maxX-aa.minX);val py=min(aa.maxY-bb.minY,bb.maxY-aa.minY);return if(px<py){val n=if(dx>=0)1f else -1f;PhysicsV2Contact(a.id,b.id,n,0f,max(0f,px),(a.position.x+b.position.x)/2f,(a.position.y+b.position.y)/2f,a.sensor||b.sensor)}else{val n=if(dy>=0)1f else -1f;PhysicsV2Contact(a.id,b.id,0f,n,max(0f,py),(a.position.x+b.position.x)/2f,(a.position.y+b.position.y)/2f,a.sensor||b.sensor)}}
    private fun resolve(a:V2Body,b:V2Body,c:PhysicsV2Contact){val inv=a.invMass+b.invMass;if(inv<=0f)return;val correction=(c.penetration*0.8f/inv).coerceAtMost(8f);if(a.dynamic()){a.position.x-=c.normalX*correction*a.invMass;a.position.y-=c.normalY*correction*a.invMass;a.grounded=c.normalY<-.5f};if(b.dynamic()){b.position.x+=c.normalX*correction*b.invMass;b.position.y+=c.normalY*correction*b.invMass;b.grounded=c.normalY>.5f};val rvx=b.velocity.x-a.velocity.x;val rvy=b.velocity.y-a.velocity.y;val vn=rvx*c.normalX+rvy*c.normalY;if(vn>0f)return;val e=min(a.material.restitution,b.material.restitution);val impulse=-(1f+e)*vn/inv;if(a.dynamic()){a.velocity.x-=c.normalX*impulse*a.invMass;a.velocity.y-=c.normalY*impulse*a.invMass};if(b.dynamic()){b.velocity.x+=c.normalX*impulse*b.invMass;b.velocity.y+=c.normalY*impulse*b.invMass}}
    private fun solveJoint(j:V2Joint,dt:Float){val a=bodies[j.def.bodyA]?:return;val b=bodies[j.def.bodyB]?:return;val dx=b.position.x-a.position.x;val dy=b.position.y-a.position.y;val d=sqrt(dx*dx+dy*dy).coerceAtLeast(.0001f);val nx=dx/d;val ny=dy/d;val error=d-j.def.length;val rel=(b.velocity.x-a.velocity.x)*nx+(b.velocity.y-a.velocity.y)*ny;val force=when(j.def.type){PhysicsV2JointType.DISTANCE->error/dt;PhysicsV2JointType.SPRING->error*j.def.stiffness+rel*j.def.damping;PhysicsV2JointType.FIXED->error/dt};if(a.dynamic()){a.velocity.x+=nx*force*a.invMass*dt;a.velocity.y+=ny*force*a.invMass*dt};if(b.dynamic()){b.velocity.x-=nx*force*b.invMass*dt;b.velocity.y-=ny*force*b.invMass*dt}}

    fun queryAabb(query:PhysicsV2Aabb):List<Int>{return bodies.values.filter{aabb(it).overlaps(query)}.map{it.id}}
    fun rayCast(x:Float,y:Float,dx:Float,dy:Float,maxDistance:Float=10000f):PhysicsV2RayHit?{val len=sqrt(dx*dx+dy*dy).coerceAtLeast(.0001f);val ux=dx/len;val uy=dy/len;var best:PhysicsV2RayHit?=null;bodies.values.forEach{b->val box=aabb(b);var tmin=0f;var tmax=maxDistance;fun axis(o:Float,d:Float,minV:Float,maxV:Float):Boolean{if(abs(d)<1e-6f)return o in minV..maxV;val inv=1f/d;var t1=(minV-o)*inv;var t2=(maxV-o)*inv;if(t1>t2){val q=t1;t1=t2;t2=q};tmin=max(tmin,t1);tmax=min(tmax,t2);return tmin<=tmax};if(axis(x,ux,box.minX,box.maxX)&&axis(y,uy,box.minY,box.maxY)&&tmin<=maxDistance){val hx=x+ux*tmin;val hy=y+uy*tmin;val hit=PhysicsV2RayHit(b.id,tmin/maxDistance,hx,hy,0f,0f);if(best==null||hit.fraction<best!!.fraction)best=hit}};return best}
    fun stats():PhysicsV2Stats=PhysicsV2Stats(stepCount,bodies.size,bodies.values.count{it.dynamic()&&it.awake},currentPairs.size,contacts.size,beginCount,stayCount,endCount)
    private fun pair(a:Int,b:Int):Long{val x=min(a,b).toLong();val y=max(a,b).toLong();return(x shl 32) xor(y and 0xffffffffL)}
    private fun pairA(k:Long)= (k shr 32).toInt();private fun pairB(k:Long)=k.toInt()
}

class PhysicsV2BodyHandle internal constructor(private val world:PhysicsWorldV2,private val id:Int){
    fun id()=id
    fun position():PhysicsV2Vec2=world.bodyState(id)?.position?.copy()?:PhysicsV2Vec2()
    fun velocity():PhysicsV2Vec2=world.bodyState(id)?.velocity?.copy()?:PhysicsV2Vec2()
    fun setVelocity(x:Float,y:Float){world.bodyState(id)?.velocity?.set(x,y)}
    fun setPosition(x:Float,y:Float){world.bodyState(id)?.position?.set(x,y)}
    fun applyForce(x:Float,y:Float){world.bodyState(id)?.let{if(it.dynamic()){it.force.x+=x;it.force.y+=y;it.wake()}}}
    fun applyImpulse(x:Float,y:Float){world.bodyState(id)?.let{if(it.dynamic()){it.velocity.x+=x*it.invMass;it.velocity.y+=y*it.invMass;it.wake()}}}
    fun setAwake(value:Boolean){world.bodyState(id)?.let{it.awake=value;if(value)it.wake()}}
}

private fun PhysicsWorldV2.bodyState(id:Int):V2Body?=this.run{val f=javaClass.getDeclaredField("bodies");f.isAccessible=true;(f.get(this) as LinkedHashMap<*,*>)[id] as? V2Body}
