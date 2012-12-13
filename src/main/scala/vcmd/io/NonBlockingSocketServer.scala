package vcmd.io

import akka.actor._
import akka.actor.ActorDSL._
import java.net.InetSocketAddress
import akka.util._
import java.net._
import java.io._
import akka.actor.IO._
import scala.concurrent.duration._

class NonBlockingSocketServer(port: Int, inputHandler: IO.SocketHandle => IO.Iteratee[Unit]) extends Actor {
  val state = IO.IterateeRef.Map.async[IO.Handle]()(context.dispatcher)
  override def preStart {
    IOManager(context.system) listen new InetSocketAddress(port)
  }
  def receive = {
    case IO.NewClient(server) =>
      val socket = server.accept()
      println(s"Accept $socket")
      state(socket) flatMap (_ => inputHandler(socket))
    case IO.Read(socket, bytes) =>
      state(socket)(IO Chunk bytes)
    case IO.Closed(socket, cause) => 
   println(s"Closing $socket")
      state(socket)(IO.EOF)
      state -= socket
  }
}