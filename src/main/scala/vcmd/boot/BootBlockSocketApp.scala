package vcmd.boot

import java.net._
import scala.concurrent.duration._
import org.apache.commons.pool.BasePoolableObjectFactory
import org.apache.commons.pool.ObjectPool
import org.apache.commons.pool.impl.GenericObjectPoolFactory
import akka.actor._
import akka.actor.ActorDSL._
import akka.actor.IO._
import akka.actor.SupervisorStrategy._
import akka.util._
import akka.util.Timeout.durationToTimeout
import vcmd.BlockingSyslogDispatcher
import vcmd.PoolHandlerActor
import vcmd.RiskShieldSenderActor
import vcmd.RiskShieldSenderMasterActor
import vcmd.ThrottlerActor
import vcmd.config._
import vcmd.io._
import org.apache.commons.pool.impl.GenericObjectPool
object BootBlockSocketApp extends App {
  implicit val timeout: Timeout = 4 seconds
  val system = ActorSystem("vcmd")
  val settings = Settings(system)
  import settings._
  implicit val dispatcher = system.dispatcher

  val router = system.actorOf(Props(new RiskShieldSenderMasterActor(Props[RiskShieldSenderActor])))

  val actorRefPool = createObjectPool(system)

  val workerFactory = (socket: Socket) => new HaltableWorkerImpl(socket, BlockingSyslogDispatcher.processRequest(actorRefPool, system))

  val adminServer = system.actorOf(Props(new VcmdAdminServerActor(new HaltableSocketServer(syslogListenerPort, workerFactory))))

  val poolHandlerActor = system.actorOf(Props(new PoolHandlerActor(actorRefPool)))

   private def createObjectPool(system: ActorSystem): ObjectPool[ActorRef] = {
    val maxActive = 50
    val whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_BLOCK
    val maxWait = -1l
    val maxIdle = -1
    val minIdle = 10
    val testOnBorrow = true
    val testOnReturn = false
    val timeBetweenEvictionRunsMillis = -1l// 1000l * 60l * 10l
    val numTestsPerEvictionRun = -1
    val minEvictableIdleTimeMillis =  -1l //1000l * 60l * 10l
    val testWhileIdle = false
    val softMinEvictableIdleTimeMillis = -1l

    new GenericObjectPoolFactory(new BasePoolableObjectFactory[ActorRef] {
      override def makeObject: ActorRef = {
        val actorRef = system.actorOf(Props[RiskShieldSenderActor])
        println(s"Create object ${actorRef}")
        actorRef
      }

      override def destroyObject(actorRef: ActorRef) = {
        println(s"Destroy object $actorRef")
        actorRef ! PoisonPill
      }

      override def validateObject(actorRef: ActorRef) = {
        //println(s"Validate object ${actorRef.isTerminated}")
        !actorRef.isTerminated
      }

    }, maxActive, whenExhaustedAction, maxWait, maxIdle, minIdle, testOnBorrow, testOnReturn, timeBetweenEvictionRunsMillis, numTestsPerEvictionRun, minEvictableIdleTimeMillis, testWhileIdle, softMinEvictableIdleTimeMillis).createPool
  }
}

