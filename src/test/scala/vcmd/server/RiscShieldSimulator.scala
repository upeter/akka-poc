package vcmd.server

import akka.actor._
import akka.actor.ActorDSL._
import akka.util._
import java.net._
import java.io._
import akka.actor.IO._
import scala.concurrent.duration._
import vcmd.io._
import scala.concurrent.ExecutionContext.Implicits.global
import vcmd._

class RiscShieldServer(system: ActorSystem) {
  import scala.concurrent.ExecutionContext.Implicits.global
  private var connectionState = Map[IO.Handle, ActorRef]()
  val perfAnalyser = system.actorOf(Props[PerfAnalyserActor])

  def closeSocketDefault(socket: IO.Handle): Unit = {
    connectionState.get(socket).map(_ ! PoisonPill)
    connectionState -= socket
  }

  def openSocketDefault(socket: IO.Handle): Unit = connectionState += socket -> system.actorOf(Props[RiskShieldWorkerActor])

  def processRequest(socket: IO.SocketHandle, nioServerActor: ActorRef, scheduler: Scheduler): IO.Iteratee[Unit] = {

    def adminTask(input: String) = {
      if (input.startsWith("reset")) {
        perfAnalyser ! ResetAnalyser
        println("reset ok")
      } else if (input.startsWith("stop")) {
        nioServerActor ! StopListening
        perfAnalyser ! ResetAnalyser
        println("stop ok")
      } else if (input.startsWith("restart")) {
        nioServerActor ! RestartListening
        println("restart ok")
      } else if (input.startsWith("stop-delay-restart")) {
        nioServerActor ! StopListening
        perfAnalyser ! ResetAnalyser
        scheduler.scheduleOnce(10 seconds) {
          nioServerActor ! RestartListening
        }
        println("stop-delay-restart ok")
      }
    }

    IO repeat {
      for {
        bytes <- IO takeUntil ByteString("\n")
      } yield {
        val input = bytes.utf8String
        adminTask(input)
        connectionState(socket) ! MessageReceived(input, socket)
        perfAnalyser ! input
        //        val resp = s"echo: $input\n"
        //        socket write (ByteString(resp, "utf-8"))
      }
    }

  }

  private def incrementAndMeasure() = {
  }

}

case class MessageReceived(msg: String, socket: IO.SocketHandle)
case object ResetAnalyser

class RiskShieldWorkerActor extends Actor {

  private val SamResponseHeader = "IDVAR_ACK	IDVAR_FW_SESSIE_ID	IDVAR_M_KLANT_COOKIE_N	IDVAR_M_KLANT_IP_N	IDVAR_M_KLANT_IP_SES_N	IDVAR_NEWSESFORCLIENT"

  private def sendInitResponse(socket: IO.SocketHandle, input: String) = {
    write(socket, SamResponseHeader)
    sendResponse(socket, input)
  }

  private def sendResponse(socket: IO.SocketHandle, input: String) = write(socket, s"1\t${input.replace("\t", ";")}")

  private def write(socket: IO.SocketHandle, resp: String) = socket write (ByteString(s"$resp\n", "utf-8"))

  def init:Receive = {
    case MessageReceived(input, socket) =>
        context become firstRequest
  }
  
  def firstRequest:Receive = {
    case MessageReceived(input, socket) =>
        sendInitResponse(socket, input)
        context become normalRequests
  }
  
  def normalRequests:Receive = {
        case MessageReceived(input, socket) =>
        //      val resp = s"echo: $input\n"
        sendResponse(socket, input)
  }
  
  def receive: Receive = init
  
}

class PerfAnalyserActor extends Actor {
  var counter = 0l
  var startTime: Long = 0
  val PrintTpsIntervalMessageCount = 100000
  val PrintMessageCountInterval = 10000

  def receive: Receive = {
    case ResetAnalyser =>
      counter = 0l
      startTime = System.currentTimeMillis()
    case msg => {
      counter += 1
      if (startTime == 0) {
        startTime = System.currentTimeMillis()
      }
      if (counter % PrintTpsIntervalMessageCount == 0) {
        val stopTime = System.currentTimeMillis()
        val elapsed = stopTime - startTime
        println(s"processed $counter messages in ${elapsed} ms resulting in ${counter / (if (elapsed == 0) 1000 else elapsed / 1000)} tps")
      } else if (counter % PrintMessageCountInterval == 0) {
        println(s"Processed $counter since $startTime")
      }
    }

  }
}

object RiscShieldSimulator extends App {
  val riskShieldServerPort = 2345
  val host = "localhost"
  val system = ActorSystem("risk-shield")
  val server = new RiscShieldServer(system)
  system.actorOf(Props(new NIOSocketServer(riskShieldServerPort, server.processRequest, server.openSocketDefault, server.closeSocketDefault)), "riskShieldlistener")
}




//         //println(s"receive $input")
//          try {
//            val i = input.toInt
//            if (i == 1) {
//              start = System.currentTimeMillis()
//              println(start)
//            }
//            if (i % 10000 == 0) println(input)
//            if (i == 500000) {
//             val stop = System.currentTimeMillis()  
//             val elapsed = stop - start
//            println(s"processed 500000 messages in ${elapsed} resulting in ${500000 / (elapsed / 1000)} tps" )
//            }
//          } catch {
//            case e => println(e.getMessage)
//          }


