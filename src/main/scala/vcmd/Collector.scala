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
import scala.util.{ Try, Failure, Success }
import io.NonBlockingSocketServer
import scala.concurrent.Await
import akka.actor.SupervisorStrategy._
import akka.dispatch.Terminate
import config.Settings

package object vcmd {
  implicit val timeout: Timeout = 2 seconds

  def validateResult(in: String)(resp: String) = {

    val expected = s"echo: $in"
    val equal = expected.startsWith(resp)
    if (!equal) {
      println(s"expected $expected ${expected.size}")
      println(s"received $resp ${resp.size}")
      //assert(equal)
    }
    equal

  }
}

sealed trait Message { def msg: String }
case class RawMessage(msg: String) extends Message
case class InitMessage(msg: String, meta: String) extends Message
//case class RetryMessage(msg: Message)
case class SendFailure(actorRef: ActorRef)
case class RiskShieldTimeout(exMsg: String)
case object StopConnection
case object StartConnection

object SyslogListener {
  val riskShieldSender = new RiskShieldSender("localhost", 2345, 2000)
  import scala.concurrent.ExecutionContext.Implicits.global
  def processRequest(processor: ActorRef)(socket: IO.SocketHandle, ref: ActorRef, scheduler: Scheduler): IO.Iteratee[Unit] = {

    IO repeat {
      for {
        bytes <- IO takeUntil ByteString("\n")
      } yield {
        val msg = bytes.decodeString("utf-8")
        //println(msg)
        //        val resp = riskShieldSender.send(msg)
        //        validateResult(msg)(resp)
        processor ! (RawMessage(msg))
      }
    }
  }

}

object SyslogDispatcher {

  def processRequest(processor: ActorRef)(msg: String) = {
    //println(s"processing line $msg")
    processor ! (RawMessage(msg))
  }

}

class ConnectionControllerActor extends Actor with ActorLogging {

  var stats = Map[String, Seq[Long]]().withDefaultValue(Seq[Long]())

  def receive: Receive = {
    case SendFailure(actorRef) => //stats += actorRef.path -> (stats("actor1") :+ 3l)
  }
}
import vcmd._
class RiskShieldSenderActor extends Actor with ActorLogging with Stash {
  import context.dispatcher

  val settings = Settings(context.system)
  import settings._
  val scheduler = context.system.scheduler
  var riskShieldSender = initSender

  private def initSender = new RiskShieldSender(riskShieldServerHost, riskShieldServerPort, riskShieldServerReadTimeout)

  context.become(initializing)

  def initializing: Receive = {
    case RawMessage(msg) =>
      log.debug("send init message...")
      //lookup metadata in cache
      send(InitMessage(msg, "Metadata"), onSuccess = (req, resp) => {
        log.debug("init sent: -> validate...")
        //TODO: handle invalid return messages
        if (validateResult(req)(resp)) {
          log.debug("init ok: -> become receive")
          context.become(receive)
        }
      })
  }
  def receive = {
    case msg @ RawMessage(_) => send(msg)

  }

  def reconnect: Receive = {
    case msg: InitMessage => {
      log.debug(s"retry message=[${msg}]...")
      send(msg, onSuccess = (req, resp) => {
        //TODO: handle invalid return messages
        if (validateResult(req)(resp)) {
          log.debug("retry ok: unstashAll -> become receive")
          unstashAll
          context.become(receive)
        }
      })
    }
    case message => stash
  }

  private def doSend(msg: String): Try[String] = {
    val result = Try(riskShieldSender.send(msg))
    if (result.isFailure) {
      val Failure(e) = result
      val reason = s"${e.getClass().getSimpleName()} occured ${e.getMessage()}. Close connection."
      log.error(reason)
      riskShieldSender.disconnect
      riskShieldSender = initSender
    }
    result
  }

  private def handleFailureDefault(msg: InitMessage) = {
    log.error(s"Failure occured. Schedule retry in ${riskShieldRetryInterval} millis.")
    scheduler.scheduleOnce(riskShieldRetryInterval milliseconds) {
      self ! msg
    }
    log.debug("send failed: -> become reconnect")
    context.become(reconnect)
  }

  private def handleSuccessDefault(req: String, resp: String): Unit = {
    validateResult(req)(resp)
  }

  private def send(msg: Message, onSuccess: (String, String) => Unit = handleSuccessDefault, onFailure: InitMessage => Unit = handleFailureDefault) = {
    msg match {
      case raw @ RawMessage(msg) =>
        val res = doSend(msg)
        handleResult(res, InitMessage(msg, "Metadata"))
      case init @ InitMessage(msg, meta) => {
        val res = for {
          metaResp <- doSend(meta)
          if validateResult(meta)(metaResp)
          resp <- doSend(msg)
          if validateResult(msg)(resp)
        } yield resp
        handleResult(res, init)

      }
      case unkown => throw new IllegalStateException(s"Invalid message type: ${unkown.getClass().getName()}")

    }
    def handleResult(res: Try[String], reqMsg: InitMessage) = {
      res match {
        //TODO: handle invalid return messages
        case Success(resp) => onSuccess(reqMsg.msg, resp)
        case Failure(e) => onFailure(reqMsg)
      }
    }
  }

}

class RiskShieldSender(host: String, port: Int, readTimeout: Int) {

  lazy val riskShieldSocket = {
    val adr = new InetSocketAddress(host, port)
    val socket = new Socket()
    socket.setSoTimeout(readTimeout)
    socket.connect(adr)
    socket
  }
  def send(msg: String): String = {
    val out = new PrintWriter(new OutputStreamWriter(riskShieldSocket.getOutputStream(), "utf-8"), true);
    val in = new BufferedReader(new InputStreamReader(
      riskShieldSocket.getInputStream(), "utf-8"));
    out.println(msg)
    Option(in.readLine()) match {
      case Some(resp) => resp
      case None =>
        throw new SocketReadException("No response received")
    }

  }

  def disconnect {
    try {
      if (riskShieldSocket.isConnected())
        riskShieldSocket.close()
    } catch {
      case e: Exception => println("disconnect failed " + e.getMessage())
    }

  }
}

class SocketReadException(msg: String) extends Exception(msg)

object SupervisorStrategy {
  val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 3,
      withinTimeRange = 1 minute) {
        //case _: NullPointerException => Restart/Stop/Resume
        case _: SocketException => Stop
        case _: SocketTimeoutException => Stop
        case _: SocketReadException =>
          println("socket read ex"); Stop
        case _: ConnectException =>
          println("socket conn ex"); Stop
        case e => println("unkown:" + e.getMessage); Stop
      }
}

class SyslogProcessorMasterActor(props: Props) extends Actor with ActorLogging {
  import context.dispatcher
  def initWorkers = {
    val router = context.system.actorOf(props.withRouter(RoundRobinRouter(20, supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 2) {
      case e => println(s"====> exception: ${e.getClass.getName} messsage ${e.getMessage}"); Restart
    })), name = "router")
    context.watch(router)
    router
  }
  var router = initWorkers

  def receive = {

    case Terminated(routerRef) =>
      println("Terminated")
      context.system.scheduler.scheduleOnce(5 seconds) {
        println("Restarting")
        router = initWorkers

      }
    case a => router forward a
  }
}

