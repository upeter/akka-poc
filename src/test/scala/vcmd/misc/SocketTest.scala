package vcmd.misc

import java.io.BufferedReader
import java.net.Socket
import java.io.PrintWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object SocketTest extends App {

(1 to 5).par.map(i => new Tester).foreach(_.test)
}
  class Tester {

  val host = "localhost"
  val port = 2345
  val riscShieldSocket = new Socket(host, port)
  def send(msg: String): String = {
    //    val outToServer = riscShieldSocket.getOutputStream()
    //    val out = new DataOutputStream(outToServer)
    //    out.writeUTF(msg)
    //    val inFromServer = riscShieldSocket.getInputStream()
    //    val in = new DataInputStream(inFromServer)
    //    in.readUTF()

    val out = new PrintWriter(new OutputStreamWriter(riscShieldSocket.getOutputStream(), "utf-8"), true);
    val in = new BufferedReader(new InputStreamReader(
      riscShieldSocket.getInputStream(), "utf-8"));
    out.println(msg)
   in.readLine()
  }
def  test() {
  val res = 1 to 100000 forall { i =>

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
  println(res);

}
}