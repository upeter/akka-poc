package vcmd

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
import io.NonBlockingSocketServer

object RiscShieldServer {
  import scala.concurrent.ExecutionContext.Implicits.global

  def processRequest(socket: IO.SocketHandle, ref:ActorRef, scheduler:Scheduler): IO.Iteratee[Unit] = {
//  var counter = 0
//  var startTime: Long = 0
    IO repeat {
      for {
        bytes <- IO takeUntil ByteString("\n")
      } yield {
        val input = bytes.utf8String
        	
//        if (counter == 0) {
//          startTime = System.currentTimeMillis
//        }
//        counter += 1
//        if (counter % 100 == 0) {
//          println(s"count: $counter elapsed ${System.currentTimeMillis - startTime}")
//        }
        if(input.startsWith("stop")) {
          ref ! "shutdown"
          scheduler.scheduleOnce(10 seconds) {
            ref ! "startup"
          }
        }
         else {
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
  system.actorOf(Props(new NonBlockingSocketServer(riskShieldServerPort, RiscShieldServer.processRequest)), "riskShieldlistener")
}


