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
import akka.actor.SupervisorStrategy._
import akka.dispatch.Terminate
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

class SyslogProcessorActor(remoteHost: String, remotePort: Int, readTimeout: Int) extends Actor with ActorLogging {
  import context.dispatcher
  val riskShieldSender = new RisShieldSender(remoteHost, remotePort, readTimeout)

  def receive = {
    case RawMessage(msg) =>
      //println(msg)
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

class RisShieldSender(host: String, port: Int, readTimeout: Int) {

  lazy val riscShieldSocket = {
    val adr = new InetSocketAddress(host, port)
    val socket = new Socket()
    socket.setSoTimeout(readTimeout)
    socket.connect(adr)
    socket
  }
  def send(msg: String): String = {
    val out = new PrintWriter(new OutputStreamWriter(riscShieldSocket.getOutputStream(), "utf-8"), true);
    val in = new BufferedReader(new InputStreamReader(
      riscShieldSocket.getInputStream(), "utf-8"));
    out.println(msg)
    Option(in.readLine()) match {
      case Some(resp) => resp
      case None => throw new SocketReadException
    }
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

class SocketReadException extends Exception

object SupervisorStrategy {
  val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 3,
      withinTimeRange = 1 minute) {
        //case _: NullPointerException => Restart/Stop/Resume
        case _: SocketException => Stop
        case _: SocketTimeoutException => Stop
        case _: SocketReadException =>
          println("socket read ex"); Stop
        case _: ConnectException =>
          println("socket conn ex"); Stop
        case e => println("unkown:" + e.getMessage); Stop
      }
}

class SyslogProcessorMasterActor(props: Props) extends Actor with ActorLogging {
  import context.dispatcher
  def initWorkers = {
    val router = context.system.actorOf(props.withRouter(RoundRobinRouter(20, supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 2) {
      case e => println(s"====> exception: ${e.getClass.getName} messsage ${e.getMessage}"); Restart
    })), name = "router")
    context.watch(router)
    router
  }
  var router = initWorkers

  def receive = {

    case Terminated(routerRef) =>
      println("Terminated")
      context.system.scheduler.scheduleOnce(5 seconds) {
        println("Restarting")
        router = initWorkers

      }
    case a => router forward a
  }
}

object Collector extends App {
  implicit val timeout: Timeout = 4 seconds
  val readTimeout = 1000
  val syslogListenerPort = 1234
  val riskShieldServerPort = 2345
  val host = "localhost"
  val system = ActorSystem("vcmd")
  implicit val dispatcher = system.dispatcher
  val router = system.actorOf(Props(new SyslogProcessorMasterActor(new Props().withCreator(new SyslogProcessorActor(host, riskShieldServerPort, readTimeout)))))
 
  val syslogListener = system.actorOf(Props(new NonBlockingSocketServer(syslogListenerPort, SyslogListener.processRequest(router))), "sysloglistener")
}

/*
 *  router with supervised children
  val supervisor = system.actorOf(Props[RiskShieldProcessorSupervisor])
  val routeesConfig: Seq[Future[ActorRef]] = for (i <- 1 to 1) yield (supervisor ? new Props().withCreator(new SyslogProcessorActor(host, riskShieldServerPort, readTimeout))).mapTo[ActorRef]
  val supervisedRoutees: Seq[ActorRef] = Await.result(Future.sequence(routeesConfig), 1.seconds)
  val router = system.actorOf(Props[RiskShieldProcessorSupervisor].withRouter(
    RoundRobinRouter(routees = supervisedRoutees)))

	* router that is supervisor
  val routees:Seq[ActorRef] = 1 to 1 map (i => system.actorOf(new Props().withCreator(new SyslogProcessorActor(host, riskShieldServerPort, readTimeout)), s"actor_$i"))
  val router = system.actorOf(Props[RiskShieldProcessorSupervisor].withRouter(
    RoundRobinRouter(routees = routees.map(_.path.toString), supervisorStrategy = SupervisorStrategy.supervisorStrategy)))

* inline router 
 //  val router = system.actorOf(new Props().withCreator(new SyslogProcessorActor(host, riskShieldServerPort, readTimeout))
  //    .withRouter(RoundRobinRouter(1, supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 2) {
  //      case e => println(s"====> exception: ${e.getClass.getName} messsage ${e.getMessage}"); Restart
  //    })), name = "router")
* 
*  */
