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
import config.Settings
package object vcmd {
  implicit val timeout: Timeout = 2 seconds
}

case class RawMessage(msg: String)
case class RetryMessage(msg: String)
case class SendFailure(actorRef:ActorRef)
case class RiskShieldTimeout(exMsg: String)
case object StopConnection
case object StartConnection

object SyslogListener {
    val riskShieldSender = new RisShieldSender("localhost", 2345, 2000)

  def processRequest(processor: ActorRef)(socket: IO.SocketHandle, ref: ActorRef, scheduler: Scheduler): IO.Iteratee[Unit] = {

    IO repeat {
      for {
        bytes <- IO takeUntil ByteString("\n")
      } yield {
        val msg = bytes.decodeString("utf-8")
        //println(msg)
//        val resp = riskShieldSender.send(msg)
//        validateResult(msg)(resp)
        processor ! (RawMessage(msg))
      }
    }
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



class ConnectionControllerActor extends Actor with ActorLogging {
  
var stats = Map[String, Seq[Long]]().withDefaultValue(Seq[Long]())

  def receive:Receive = {
    case SendFailure(actorRef) => //stats += actorRef.path -> (stats("actor1") :+ 3l)
  }
}


class SyslogProcessorActor extends Actor with ActorLogging with Stash {
  import context.dispatcher
   val settings = Settings(context.system)
   import settings._
  val riskShieldSender = new RisShieldSender(riskShieldServerHost, riskShieldServerPort, riskShieldServerReadTimeout)
 
  
  def initializing:Receive = {
    case RawMessage(msg) => 
  }

  
    def reconnect:Receive = {
    case RetryMessage(msg) => {
      if(false) {
        //retry failed:
        //schedule new retry
      } else {
        //retry successful:
        unstashAll
        //notify connection controller
        //become receive mode
      }
      
    }
    case RawMessage(msg) => stash
  }

  def receive = {
    case RawMessage(msg) =>
      //println(msg)
      val resp = riskShieldSender.send(msg);
      validateResult(msg)(resp)
      if(false) {
        //send Failed to connection controler
        //schedule retry with current message
        //become reconnect mode 
      } else {
        //forward to file writer actor
      }
      
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
  val system = ActorSystem("vcmd")
  val settings = Settings(system)
  import settings._
  implicit val dispatcher = system.dispatcher
  val router = system.actorOf(Props(new SyslogProcessorMasterActor( Props[SyslogProcessorActor])))
println(settings.riskShieldServerConnectTimeout)
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

