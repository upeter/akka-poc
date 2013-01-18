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

class RiscShieldServer(actorRef: ActorRef) {
  import scala.concurrent.ExecutionContext.Implicits.global
var start:Long = _
  def processRequest(socket: IO.SocketHandle, ref: ActorRef, scheduler: Scheduler): IO.Iteratee[Unit] = {

    def adminTask(input: String) = {
      if (input.startsWith("reset")) {
        actorRef ! ResetAnalyser
        println("reset ok")
      } else if (input.startsWith("stop")) {
        ref ! StopListening
        actorRef ! ResetAnalyser
        println("stop ok")
      } else if (input.startsWith("restart")) {
        ref ! RestartListening
        println("restart ok")
      } else if (input.startsWith("stop-delay-restart")) {
        ref ! StopListening
        actorRef ! ResetAnalyser
        scheduler.scheduleOnce(10 seconds) {
          ref ! RestartListening
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
        actorRef ! input
        val resp = s"echo: $input\n"
        socket write (ByteString(resp, "utf-8"))
      }
    }

  }

  private def incrementAndMeasure() = {
  }

}

case object ResetAnalyser

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
      if (counter % PrintTpsIntervalMessageCount  == 0) {
        val stopTime = System.currentTimeMillis()
        val elapsed = stopTime - startTime 
        println(s"processed $counter messages in ${elapsed} ms resulting in ${counter / (if (elapsed == 0) 1000 else elapsed / 1000)} tps")
      } else if (counter % PrintMessageCountInterval  == 0) {
        println(s"Processed $counter since $startTime")
      }
    }

  }
}

object RiscShieldSimulator extends App {
  val riskShieldServerPort = 2345
  val host = "localhost"
  val system = ActorSystem("risk-shield")
  val perfAnalyser = system.actorOf(Props[PerfAnalyserActor])
  system.actorOf(Props(new NIOSocketServer(riskShieldServerPort, new RiscShieldServer(perfAnalyser).processRequest)), "riskShieldlistener")
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


