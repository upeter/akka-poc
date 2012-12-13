package vcmd.misc

import akka.actor._
import akka.actor.ActorDSL._
import java.net.InetSocketAddress
import akka.util._
import java.net._
import java.io._

class EchoServer(port: Int) extends Actor with ActorLogging {
  override def preStart {
    IOManager(context.system) listen new InetSocketAddress(port)
  }

  def receive = {
    case IO.NewClient(server) =>
      server.accept()
    case IO.Read(socket, bytes) => {
      val v = bytes.decodeString("utf-8")
      //log.info("remote server got {} from {}", v, socket)
      socket.asWritable.write(ByteString("echo: " + v))
    }
  }
}
class EchoClient(port: Int, remoteHost: String, remotePort: Int) extends Actor with ActorLogging {
  val echoServerSender = new EvilBlockingSender(remoteHost, remotePort)
  override def preStart {
    IOManager(context.system) listen new InetSocketAddress(port)
  }
  def receive = {
    case IO.NewClient(server) =>
      server.accept()
    case IO.Read(socket, bytes) => {
      val msg = bytes.decodeString("utf-8")
      //log.info("got {} from {}", msg, socket)
      val resp = echoServerSender.send(msg)
      socket.asWritable.write(ByteString(resp + "\n"))
    }
  }
}

class EvilBlockingSender(host: String, port: Int) {
  lazy val echoSocket = new Socket(host, port);
  def send(msg: String): String = {
    val out = new PrintWriter(echoSocket.getOutputStream(), true);
    val in = new BufferedReader(new InputStreamReader(
      echoSocket.getInputStream()));
    out.println(msg);
    in.readLine()
  }
}

object Echo extends App {
  val echoServerPort = 2345
  val echoClientPort = 1234
  val host = "localhost"
  val system = ActorSystem()
  val server = system.actorOf(Props(new EchoServer(echoServerPort)))
  val client = system.actorOf(Props(new EchoClient(echoClientPort, host, echoServerPort)))
}