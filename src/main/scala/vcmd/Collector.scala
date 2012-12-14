package vcmd

import akka.actor._
import akka.actor.ActorDSL._
import java.net.InetSocketAddress
import akka.util._
import java.net._
import java.io._
import akka.pattern.ask
import akka.actor.IO._
import akka.routing.RoundRobinRouter
import scala.concurrent.{ Future, Promise, future }
import scala.concurrent.duration._
import scala.util.{ Try, Failure, Success }
import io.NonBlockingSocketServer
import scala.concurrent.Await
package object vcmd {
  implicit val timeout: Timeout = 2 seconds
}

case class RawMessage(msg: String)
case class RiskShieldTimeout(exMsg: String)
case object StopConnection
case object StartConnection

object SyslogListener {

  def processRequest(processor: ActorRef)(socket: IO.SocketHandle, ref: ActorRef, scheduler: Scheduler): IO.Iteratee[Unit] =
    IO repeat {
      for {
        bytes <- IO takeUntil ByteString("\n")
      } yield {
        val msg = bytes.decodeString("utf-8")
        processor ! (RawMessage(msg))
      }
    }
}

class SyslogProcessorActor(remoteHost: String, remotePort: Int) extends Actor with ActorLogging {
  import context.dispatcher
  val riskShieldSender = new RisShieldSender(remoteHost, remotePort)

  def receive = {
    case RawMessage(msg) =>
      val resp = riskShieldSender.send(msg);
      validateResult(msg)(resp)
    //println(resp)
    //      future {
    //        val resp = riskShieldSender.send(msg);
    //        validateResult(msg)(resp) 
    //         
    //      } recover {
    //        case s: SocketTimeoutException => println(s.getMessage())
    //         case s: Exception => println(s.getMessage())
    //      }

  }

  private def validateResult(in: String)(resp: String) = {
    println(resp)
    val expected = s"echo: $in"
    val equal = resp == expected
    if (!equal) {
      println(s"expected $expected")
      println(s"received $resp")
      //assert(equal)
    }
    equal

  }

}

class RisShieldSender(host: String, port: Int) {

  lazy val riscShieldSocket = new Socket(host, port)
  def send(msg: String): String = {
    val out = new PrintWriter(new OutputStreamWriter(riscShieldSocket.getOutputStream(), "utf-8"), true);
    val in = new BufferedReader(new InputStreamReader(
      riscShieldSocket.getInputStream(), "utf-8"));
    out.println(msg);
    in.readLine()
  }

  def disconnect {
    try {
      if (riscShieldSocket.isConnected())
        riscShieldSocket.close()
    } catch {
      case e: Exception => println("disconnect failed " + e.getMessage())
    }

  }
}

object Collector extends App {
  implicit val timeout: Timeout = 4 seconds

  val syslogListenerPort = 1234
  val riskShieldServerPort = 2345
  val host = "localhost"
  val system = ActorSystem("vcmd")
  implicit val dispatcher = system.dispatcher
  val supervisor = system.actorOf(Props[RiskShieldProcessorSupervisor])
  val routeesConfig: Seq[Future[ActorRef]] = for (i <- 1 to 4) yield (supervisor ? new Props().withCreator(new SyslogProcessorActor(host, riskShieldServerPort))).mapTo[ActorRef]
  val supervisedRoutees: Seq[ActorRef] = Await.result(Future.sequence(routeesConfig), 1.seconds)

  //val routees = 1 to 4 map (i => system.actorOf(new Props().withCreator(new SyslogProcessorActor(host, riskShieldServerPort)), s"actor_$i"))
  val router = system.actorOf(Props[RiskShieldProcessorSupervisor].withRouter(
    RoundRobinRouter(routees = supervisedRoutees)))
  val syslogListener = system.actorOf(Props(new NonBlockingSocketServer(syslogListenerPort, SyslogListener.processRequest(router))), "sysloglistener")
}
