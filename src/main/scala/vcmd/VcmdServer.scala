package vcmd

import akka.actor.Actor
import akka.actor.Props
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy._
import scala.concurrent.duration._
import java.net._
import akka.actor.Terminated
import akka.actor._
import akka.actor.ActorDSL._
import java.net.InetSocketAddress
import akka.util._
import java.net._
import java.io._
import akka.pattern.ask
import akka.actor.IO._
import akka.routing.RoundRobinRouter
import scala.concurrent.{ Future, Promise, future }
import scala.concurrent.duration._
import scala.util.{ Try, Failure, Success }
import scala.concurrent.Await
import akka.actor.SupervisorStrategy._
import akka.dispatch.Terminate
import config.Settings
import io.{ NIOSocketServer, StopListening, RestartListening }

object SyslogListener {
  val riskShieldSender = new RiskShieldSender("localhost", 2345, 2000)
  import scala.concurrent.ExecutionContext.Implicits.global
  def processRequest(processor: ActorRef, system: ActorSystem)(socket: IO.SocketHandle, self: ActorRef, scheduler: Scheduler): IO.Iteratee[Unit] = {

    IO repeat {
      for {
        bytes <- IO takeUntil ByteString("\n")
      } yield {
        val msg = bytes.decodeString("utf-8")
        //println(msg)
        //        val resp = riskShieldSender.send(msg)
        //        validateResult(msg)(resp)
        processor ! (RawMessage(msg))
        system.eventStream.publish(MessageReceived())
      }
    }
  }

}

class ThrottlerActor(socketServerActor: ActorRef) extends Actor with ActorLogging {

  private val settings = Settings(context.system)
  import settings._
  private var messagesInProgres: BigInt = 0

  listenTo(classOf[MessageReceived], classOf[MessageSent], classOf[DeadLetter])

  def receive: Receive = deadLetter orElse {
    case m: MessageReceived =>
      incrementCount()
      if (messagesInProgres > highWatermarkMessageCount) {
        log.info(s"Exceeded high watermark of $highWatermarkMessageCount messsages. Halt connections ...")
        socketServerActor ! StopListening
        context.become(highWatermarkExceeded)
      }
    case m: MessageSent => decrementCount()
  }

  def highWatermarkExceeded: Receive = deadLetter orElse {
    case m: MessageReceived => incrementCount()
    case m: MessageSent =>
      decrementCount()
      if (messagesInProgres < lowWatermarkMessageCount) {
        log.info(s"Low watermark of $lowWatermarkMessageCount messages reached. Resume connections.")
        socketServerActor ! RestartListening
        context.unbecome
      }
  }

  private def deadLetter: Receive = {
    case d: DeadLetter =>
      decrementCount()
      log.error(s"Message could not be processed ${d.message}")
  }
  private def incrementCount() = messagesInProgres += 1
  private def decrementCount() = messagesInProgres -= 1
  private def listenTo(events: Class[_]*) = events foreach { c =>
    context.system.eventStream.subscribe(self, c)
  }

}



