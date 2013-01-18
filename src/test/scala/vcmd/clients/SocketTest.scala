package vcmd.clients

import java.io.BufferedReader
import java.net.Socket
import java.io.PrintWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import SyslogClient._
object SocketTest extends App {
  try {
    val to = 500000
    val resetter = new Tester(port = 2345)
    resetter.sendAndForget("reset")
    resetter.close
    
    val tester = new Tester

    val (elapsed, _) = measure {
      1 to to foreach (i => tester.sendAndForget(s"$i"))
    }
    tester.close
  } catch {
    case e => e.printStackTrace()
  }

  //println(s"===========================================>total sent: $to, elapsed $elapsed ms, tps ${to / elapsed * 1000}")

  // (1 to 5).map(i => new Tester).foreach(_.test)
}

class SocketReadException extends Exception

class Tester(val host: String = "localhost", val port: Int = 1234) {

  val vcmdSocket = {
    val adr = new InetSocketAddress(host, port)
    val socket = new Socket()
    socket.setSoTimeout(1000)
    socket.connect(adr)
    socket
  }
  def sendAndReceive(msg: String, times: Int = 1): String = {
    try {
      val out = new PrintWriter(new OutputStreamWriter(vcmdSocket.getOutputStream(), "utf-8"), true);
      val in = new BufferedReader(new InputStreamReader(
        vcmdSocket.getInputStream(), "utf-8"));
      out.println(msg)
      val res = (1 to times) map { c =>
        Option(in.readLine()) match {          
          case Some(resp) => println(resp);resp
          case None => throw new SocketReadException
        }
      }
      res.reverse.head
    } catch {
      case e =>
        println(e.getClass().getName() + " " + e.getMessage())
        throw e
    }
  }

  def close() = vcmdSocket.close
  
  def sendAndForget(msg: String): Unit = {
    //Thread.sleep(1000)
    //println(msg)
    val out = new PrintWriter(new OutputStreamWriter(vcmdSocket.getOutputStream(), "utf-8"), true);
    out.println(msg)
    out.flush
  }

  def test() {
    val res = 1 to 10000 foreach { i =>
      Thread.sleep(300)
      val resp = sendAndReceive(i.toString)
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