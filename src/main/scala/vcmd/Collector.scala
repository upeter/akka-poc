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
import vcmd.io.NonBlockingSocketServer
package object vcmd {
  implicit val timeout: Timeout = 2 seconds
}

case class RawMessage(msg: String)
case class RiskShieldTimeout(exMsg: String)
case object StopConnection
case object StartConnection

object SyslogListener {

  def processRequest(processor: ActorRef)(socket: IO.SocketHandle): IO.Iteratee[Unit] =
    IO repeat {
      for {
        bytes <- IO takeUntil ByteString("\n")
      } yield {
        val msg = bytes.decodeString("utf-8")
        processor ! (RawMessage(msg))
      }
    }
}

class SyslogProcessor(remoteHost: String, remotePort: Int) extends Actor with ActorLogging {
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
    //println(resp)
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
 //val sockaddr = new InetSocketAddress(host, port);
//  var socket:Option[Socket] = None
//  private def riscShieldSocket = socket match {
//    case Some(s) => s
//    case None => socket = Some(connect)
//    socket.get
//  }
    
lazy val riscShieldSocket = new Socket(host, port)
  def send(msg: String): String = {
    val out = new PrintWriter(new OutputStreamWriter(riscShieldSocket.getOutputStream(), "utf-8"), true);
    val in = new BufferedReader(new InputStreamReader(
      riscShieldSocket.getInputStream(), "utf-8"));
    out.println(msg );
    in.readLine()
  }

  private def connect:Socket = {
    val socket = new Socket
    //socket.setSoTimeout(1000)
    //socket.connect(sockaddr)
    socket
  }

  def disconnect {
    try {
    
      if (riscShieldSocket.isConnected()) 
      riscShieldSocket.close()
    } catch {
      case e:Exception => println("disconnect failed " + e.getMessage())
    }
    
  }
  def reconnect {
    if (!riscShieldSocket.isClosed) {
      riscShieldSocket.close
    }
    riscShieldSocket
  }
}

object Collector extends App {
  val syslogListenerPort = 1234
  val riskShieldServerPort = 2345
  val host = "localhost"
  val system = ActorSystem("vcmd")
  val routees = 1 to 4 map (i => system.actorOf(new Props().withCreator(new SyslogProcessor(host, riskShieldServerPort)), s"actor_$i"))
  val router = system.actorOf(Props[SyslogProcessor].withRouter(
    RoundRobinRouter(routees = routees)))
  val syslogListener = system.actorOf(Props(new NonBlockingSocketServer(syslogListenerPort, SyslogListener.processRequest(router))), "sysloglistener")
  routees foreach(_ ! StartConnection)
}
*/