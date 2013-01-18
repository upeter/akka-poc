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

class NIOEchoServer(actorRef: ActorRef) {
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

}


object NIOEchoServer extends App {
  val riskShieldServerPort = 2345
  val host = "localhost"
  val system = ActorSystem("risk-shield")
  val perfAnalyser = system.actorOf(Props[PerfAnalyserActor])
  system.actorOf(Props(new NIOSocketServer(riskShieldServerPort, new NIOEchoServer(perfAnalyser).processRequest)), "riskShieldlistener")
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


