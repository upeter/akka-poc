package vcmd.io

import akka.actor._
import akka.actor.ActorDSL._
import java.net.InetSocketAddress
import akka.util._
import java.net._
import java.io._
import akka.actor.IO._
import scala.concurrent.duration._

class NonBlockingSocketServer(port: Int, inputHandler: (IO.SocketHandle, ActorRef, Scheduler) => IO.Iteratee[Unit]) extends Actor {
  val state = IO.IterateeRef.Map.async[IO.Handle]()(context.dispatcher)
  var serverHanlde: Option[ServerHandle] = None
  val scheduler = context.system.scheduler
  override def preStart {
    startListening
  }
  def receive = {
    case IO.NewClient(server) =>
      val socket = server.accept()
      println(s"Accept $socket")
      state(socket) flatMap (_ => inputHandler(socket, self, scheduler))
    case IO.Read(socket, bytes) =>
      state(socket)(IO Chunk bytes)
    case IO.Closed(socket, cause) =>
      println(s"Closing $socket")
      state(socket)(IO.EOF)
      state -= socket
    case "shutdown" =>
      stopListening
      state.foreach {
        case (handle, iteratee) =>
          handle.close
          state -= handle
      }
    case "startup" =>
      startListening
  }

  private def stopListening {
    serverHanlde.map(_.close)
    serverHanlde = None
  }

  private def startListening {
    serverHanlde = Some(IOManager(context.system) listen new InetSocketAddress(port))
  }
}