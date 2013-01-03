package vcmd

import akka.actor.Actor
import akka.actor.Props
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy._
import scala.concurrent.duration._
import java.net._
import akka.actor.Terminated

class RiskShieldProcessorSupervisor extends Actor {
  implicit val dispatcher = context.dispatcher
  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 3,
      withinTimeRange = 1 minute) {
        //case _: NullPointerException => Restart/Stop/Resume
        case _: SocketException => Stop
        case _: SocketTimeoutException => Restart
        case _: SocketReadException =>
          println("socket read ex"); Restart
        case _: ConnectException =>
          println("socket conn ex"); Restart
        case e => println("unkown:" + e.getMessage); Stop
      }

  def receive = {
    case props: Props =>
      val child = context.actorOf(props)
      context.watch(child)
      sender ! child
    case Terminated(actorRef) =>
      println("Terminated")
      context.system.scheduler.scheduleOnce(5 seconds) {
        println("Restarting")
        actorRef ! RawMessage("restart")
        
      }
  }
}
