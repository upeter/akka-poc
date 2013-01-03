package vcmd.misc

import java.io.BufferedReader
import java.net.Socket
import java.io.PrintWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import SyslogClient._
object SocketTest extends App {
try {
    val to = 500000
    val tester = new Tester
  val (elapsed, _) = measure {
    1 to to foreach (i => tester.log(s"$i"))
  }
} catch {
  case e => e.printStackTrace()
}
    
  println(s"===========================================>total sent: $to, elapsed $elapsed ms, tps ${to / elapsed * 1000}")

 // (1 to 5).map(i => new Tester).foreach(_.test)
}

class SocketReadException extends Exception

class Tester {

  val host = "localhost"
  val port = 1234
  val riscShieldSocket = {
    val adr = new InetSocketAddress(host, port)
    val socket = new Socket()
    socket.setSoTimeout(1000)
    socket.connect(adr)
    socket
  }
  def send(msg: String): String = {
    try {
      val out = new PrintWriter(new OutputStreamWriter(riscShieldSocket.getOutputStream(), "utf-8"), true);
      val in = new BufferedReader(new InputStreamReader(
        riscShieldSocket.getInputStream(), "utf-8"));
      out.println(msg)
      Option(in.readLine()) match {
        case Some(resp) => resp
        case None => throw new SocketReadException
      }
    } catch {
      case e =>
        println(e.getClass().getName() + " " + e.getMessage())
        throw e
    }
  }
  
    def log(msg: String): Unit = {
        //Thread.sleep(1000)
        //println(msg)
      val out = new PrintWriter(new OutputStreamWriter(riscShieldSocket.getOutputStream(), "utf-8"), true);
      out.println(msg)
      out.flush
    }

  def test() {
    val res = 1 to 10000 foreach { i =>
      Thread.sleep(300)
      val resp = send(i.toString)
      val expected = s"echo: $i"
      val equal = resp == expected
      if (!equal) {
        println(s"expected $expected")
        println(s"received $resp")
        //assert(equal)
      }
      equal

    }
    //println(res);

  }
}