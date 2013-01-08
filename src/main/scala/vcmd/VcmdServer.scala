package vcmd

import akka.actor.ActorLogging
import akka.actor.Actor
import io.HaltableSocketServer
import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor._
import akka.actor.ActorDSL._
import java.net.InetSocketAddress
import akka.util._
import java.net._
import java.io._
import akka.actor.IO._
import scala.concurrent.duration._
import io._
class VcmdAdminServerActor(socketServer : => HaltableSocketServer) extends Actor with ActorLogging {

  val settings = config.Settings(context.system)
  import settings._
  val adminConnections = IO.IterateeRef.Map.async[IO.Handle]()(context.dispatcher)
  val scheduler = context.system.scheduler

  val vcmdServer = socketServer
  override def preStart {
    IOManager(context.system) listen new InetSocketAddress(adminPort)
    context.dispatcher.execute(vcmdServer)
  }

  override def postStop {
    vcmdServer.stop
  }
 
  def receive: Receive = {
    case IO.NewClient(server) =>
      val socket = server.accept()
      println(s"Accept $socket")
      adminConnections(socket) flatMap (_ => processAdminRequest(socket, self, scheduler))
    case IO.Read(socket, bytes) =>
      adminConnections(socket)(IO Chunk bytes)
    case IO.Closed(socket, cause) =>
      println(s"Closing $socket")
      adminConnections(socket)(IO.EOF)
      adminConnections -= socket
    case StopListening => vcmdServer.haltConnections
    case RestartListening => vcmdServer.resumeConnections
  }

  def processAdminRequest(socket: IO.SocketHandle, ref: ActorRef, scheduler: Scheduler): IO.Iteratee[Unit] = {
    IO repeat {
      for {
        bytes <- IO takeUntil ByteString("\n")
      } yield {
        val msg = bytes.decodeString("utf-8")
        performAdminTask(msg.init, socket)
      }
    }
  }

  private def performAdminTask(cmd: String, socket: IO.SocketHandle) = {
    def write(resp: String) = {
      socket write (ByteString(s"$resp\n", "utf-8"))
    }
    cmd match {
      case "mute" =>
        vcmdServer.haltConnections
        write("mute done")
      case "resume" =>
        vcmdServer.resumeConnections
        write("resume done")
      case none => write(s"unkown command: $none")
    }
  }

}