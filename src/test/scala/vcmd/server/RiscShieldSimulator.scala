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

object RiscShieldServer {
  import scala.concurrent.ExecutionContext.Implicits.global
var start = 0l
  def processRequest(socket: IO.SocketHandle, ref: ActorRef, scheduler: Scheduler): IO.Iteratee[Unit] = {
    //  var counter = 0
    //  var startTime: Long = 0
    IO repeat {
      for {
        bytes <- IO takeUntil ByteString("\n")
      } yield {
        val input = bytes.utf8String


        if (input.startsWith("stop")) {
          ref ! StopListening
          scheduler.scheduleOnce(10 seconds) {
            ref ! RestartListening
          }
        } else {
          //println(s"receive $input")
          try {
            val i = input.toInt
            if (i == 1) {
              start = System.currentTimeMillis()
              println(start)
            }
            if (i % 10000 == 0) println(input)
            if (i == 500000) {
             val stop = System.currentTimeMillis()  
             val elapsed = stop - start
            println(s"processed 500000 messages in ${elapsed} resulting in ${500000 / (elapsed / 1000)} tps" )
            }
          } catch {
            case e => println(e.getMessage)
          }
          val resp = s"echo: $input\n"
          socket write (ByteString(resp, "utf-8"))
        }
      }
    }
  }
}

object RiscShieldSimulator extends App {
  val riskShieldServerPort = 2345
  val host = "localhost"
  val system = ActorSystem("risk-shield")
  system.actorOf(Props(new NIOSocketServer(riskShieldServerPort, RiscShieldServer.processRequest)), "riskShieldlistener")
}

