package vcmd


import akka.actor.ActorLogging
import akka.actor.Actor
import io._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor._
import akka.actor.ActorDSL._
import java.net.InetSocketAddress
import akka.util._
import java.net._
import java.io._
import akka.actor.IO._
import scala.concurrent.duration._
import org.apache.commons.pool.impl.GenericObjectPool
import org.apache.commons.pool.ObjectPool

//listen to sent messages
//add to pool 
object BlockingSyslogDispatcher {
  
  def processRequest(actorPool:ObjectPool[ActorRef], system: ActorSystem)(msg: String) = {
    //println(s"processing line $msg")
    //borrow from Pool and send
    
    actorPool.borrowObject() ! (RawMessage(msg))
    system.eventStream.publish(MessageReceived())
  }

}


object NonBlockingSyslogDispatcher {
  
  def processRequest(processor:ActorRef, system: ActorSystem)(msg: String) = {
    //println(s"processing line $msg")
    processor ! (RawMessage(msg))
     system.eventStream.publish(MessageReceived())
  }

}


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
    case StopListening =>
         vcmdServer.haltConnections
         println("Connections halted")
    case RestartListening =>
       vcmdServer.resumeConnections
         println("Connections resumed")
      
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

class PoolHandlerActor(actorRefPool: ObjectPool[ActorRef]) extends Actor with ActorLogging {

  listenTo(classOf[MessageSent], classOf[DeadLetter])

  def receive: Receive = deadLetter orElse {
    case MessageSent(actorRef) => actorRefPool.returnObject(actorRef)
  }

  private def deadLetter: Receive = {
    case d: DeadLetter =>
      log.error(s"Message could not be processed ${d.message}")
  }
  private def listenTo(events: Class[_]*) = events foreach { c =>
    context.system.eventStream.subscribe(self, c)
  }

}