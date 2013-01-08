package vcmd.boot
import akka.actor._
import akka.actor.ActorDSL._
import akka.util._
import java.net._
import java.io._
import akka.actor.IO._
import scala.concurrent.duration._
import vcmd.io.NIOSocketServer
import akka.actor.SupervisorStrategy._
import vcmd.config.Settings
import akka.util.Timeout.durationToTimeout
import vcmd.RiskShieldSenderActor
import vcmd.SyslogListener
import vcmd._
 
object Boot extends App {
  implicit val timeout: Timeout = 4 seconds
  val system = ActorSystem("vcmd")
  val settings = Settings(system)
  import settings._
  implicit val dispatcher = system.dispatcher

  val router = system.actorOf(Props(new RiskShieldSenderMasterActor(Props[RiskShieldSenderActor])))
  val syslogListener = system.actorOf(Props(new NIOSocketServer(syslogListenerPort, SyslogListener.processRequest(router, system))), "sysloglistener")
  val throttlerActor = system.actorOf(Props(new ThrottlerActor(syslogListener)))


}
