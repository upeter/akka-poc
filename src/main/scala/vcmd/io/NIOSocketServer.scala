package vcmd.io

import akka.actor._
import akka.actor.ActorDSL._
import java.net.InetSocketAddress
import akka.util._
import java.net._
import java.io._
import akka.actor.IO._
import scala.concurrent.duration._

case object StopListening
case object RestartListening
object NIOSocketServer {
  
  def closeSocketDefault(socket:IO.Handle):Unit = ()
  def openSocketDefault(socket:IO.Handle):Unit = ()
}
class NIOSocketServer(port: Int, inputHandler: (IO.SocketHandle, ActorRef, Scheduler) => IO.Iteratee[Unit], onSocketOpen:IO.Handle => Unit = NIOSocketServer.openSocketDefault, onSocketClose:IO.Handle => Unit = NIOSocketServer.closeSocketDefault) extends Actor {
  val state = IO.IterateeRef.Map.async[IO.Handle]()(context.dispatcher)
  var serverHanlde: Option[ServerHandle] = None
  val scheduler = context.system.scheduler
  override def preStart {
    startListening
  }
  def receive = {
    case IO.NewClient(server) =>
      val socket = server.accept()
      onSocketOpen(socket)
      println(s"Accept $socket")
      state(socket) flatMap (_ => inputHandler(socket, self, scheduler))
    case IO.Read(socket, bytes) =>
      state(socket)(IO Chunk bytes)
    case IO.Closed(socket, cause) =>
      println(s"Closing $socket")
      state(socket)(IO.EOF)
      onSocketClose(socket)
      state -= socket
    case StopListening =>
      stopListening
      state.foreach {
        case (handle, iteratee) =>
          handle.close
          state -= handle
      }
    case RestartListening =>
      startListening
  }

  private def stopListening {
    serverHanlde.map(_.close)
    serverHanlde = None
  }

  private def startListening {
    if (serverHanlde == None) {
      serverHanlde = Some(IOManager(context.system) listen new InetSocketAddress(port))
    }
  }
}