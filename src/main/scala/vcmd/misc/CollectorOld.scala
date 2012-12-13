package vcmd.misc

import akka.actor._
import akka.actor.ActorDSL._
import java.net.InetSocketAddress
import akka.util._
import java.net._
import java.io._
import akka.actor.IO._
import akka.routing.RoundRobinRouter
import vcmd.RisShieldSender



class RiscShieldServerOld(port: Int) extends Actor with ActorLogging {
  override def preStart {
    IOManager(context.system) listen new InetSocketAddress(port)
  }

  def receive = {
    case IO.NewClient(server) =>
      server.accept()
    case IO.Read(socket, bytes) => {
      val v = bytes.decodeString("utf-8")
      //log.info("risc shield server got {} from {}", v, socket)
      socket.asWritable.write(ByteString("echo: " + v, "utf-8"))
    }
  }
}
class SyslogListenerOld(port: Int, processor: ActorRef) extends Actor with ActorLogging {
  override def preStart {
    IOManager(context.system) listen new InetSocketAddress(port)
  }
  def receive = {
    case IO.NewClient(server) =>
      server.accept()
    case IO.Read(socket, bytes) => {
      val msg = bytes.decodeString("utf-8")
      //log.info("got {} from {}", msg, socket)
      processor ! (RawMessageOld(msg))
    }
  }
}
case class RawMessageOld(msg: String)

class SyslogProcessorOld(remoteHost: String, remotePort: Int) extends Actor with ActorLogging {
  val riskShieldServer = new RisShieldSender(remoteHost, remotePort)
  def receive = {
    case RawMessageOld(msg) =>
      val resp = riskShieldServer.send(msg)
      val expected = s"echo: $msg"
      if (!(resp zip expected forall { case (a, b) => a == b })) {
        println(s"expected $expected")
        println(s"received $resp")
      }
  }

}

class RisShieldSenderOld(host: String, port: Int) {
  lazy val riscShieldSocket = new Socket(host, port);
  def send(msg: String): String = {
    val out = new PrintWriter(new OutputStreamWriter(riscShieldSocket.getOutputStream(), "utf-8"), true);
    val in = new BufferedReader(new InputStreamReader(
      riscShieldSocket.getInputStream(), "utf-8"));
    out.println(msg);
    in.readLine()
  }
}

object CollectorOld extends App {
  val syslogListenerPort = 1234
  val riskShieldServerPort = 2345
  val host = "localhost"
  val system = ActorSystem()
    val routees = 1 to 3 map (i => system.actorOf(new Props().withCreator(new SyslogProcessorOld(host, riskShieldServerPort))))
    val router = system.actorOf(Props[SyslogProcessorOld].withRouter(
      RoundRobinRouter(routees = routees)))
    val server = system.actorOf(Props(new SyslogListenerOld(syslogListenerPort, router)))
    val client = system.actorOf(Props(new RiscShieldServerOld(riskShieldServerPort)))
}