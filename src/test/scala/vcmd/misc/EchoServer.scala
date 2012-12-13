package vcmd.misc

import akka.actor._
import akka.actor.ActorDSL._
import java.net.InetSocketAddress
import akka.util._
import java.net._
import java.io._
object Server extends App {
  implicit val sys = ActorSystem("telnet")

  actor(new Act with ActorLogging {
    IOManager(context.system) listen new InetSocketAddress(2345)
    become {
      case IO.NewClient(server) =>
        server.accept()
      case IO.Read(socket, bytes) => {
        val v = bytes.decodeString("utf-8")
        log.info("remote server got {} from {}", v, socket)
        socket.asWritable.write(ByteString("echo: " + v))
      }
    }
  })

  actor(new Act with ActorLogging {
    lazy val echoSocket = new Socket("localhost", 2345);
    def send(msg: String) = {
      val out = new PrintWriter(echoSocket.getOutputStream(), true);
      val in = new BufferedReader(new InputStreamReader(
        echoSocket.getInputStream()));
      out.println(msg);
      in.readLine()

    }
    //val remoteServerSocket = IOManager(context.system).connect("localhost", 2345)
    IOManager(context.system) listen new InetSocketAddress(1234)
    become {
      case IO.NewClient(server) =>
        server.accept()
      case IO.Read(socket, bytes) => {
        val msg = bytes.decodeString("utf-8")
        log.info("got {} from {}", msg, socket)
        val resp = send(msg)
        socket.asWritable.write(ByteString(resp))
      }
    }
  })

} 