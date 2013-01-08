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
import scala.concurrent.Await
import akka.actor.SupervisorStrategy._
import akka.dispatch.Terminate
import config.Settings
import io.{ NIOSocketServer, StopListening, RestartListening }

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

import vcmd._
class RiskShieldSenderActor extends Actor with ActorLogging with Stash {
  import context.dispatcher

  val settings = Settings(context.system)
  import settings._
  val scheduler = context.system.scheduler
  var riskShieldSender = initSender

  private def initSender = new RiskShieldSender(riskShieldServerHost, riskShieldServerPort, riskShieldServerReadTimeout)

  def receive = initializing

  def initializing: Receive = {
    case RawMessage(msg) =>
      log.debug("send init message...")
      //TODO: lookup metadata in cache
      send(InitMessage(msg, "Metadata"), onSuccess = (req, resp) => {
        log.debug("init sent: -> validate...")
        //TODO: handle invalid return messages
        if (validateResult(req)(resp)) {
          log.debug("init ok: -> become receive")
          context.become(defaultHandling)
        }
      })
  }

  def defaultHandling: Receive = {
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
          context.become(defaultHandling)
        }
      })
    }
    case message => stash
  }

  //******************************************
  //Private helper methods
  //******************************************

  private def doSend(msg: String): Try[String] = {
    val result = Try(riskShieldSender.send(msg))
    if (result.isFailure) {
      val Failure(e) = result
      val reason = s"${e.getClass().getSimpleName()} occured ${e.getMessage()}. Close connection."
      log.error(reason)
      riskShieldSender.disconnect
      riskShieldSender = initSender
    } else {
      context.system.eventStream.publish(MessageSent())
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
        case Success(resp) =>
          onSuccess(reqMsg.msg, resp)
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

class RiskShieldSenderMasterActor(props: Props) extends Actor with ActorLogging {
  import context.dispatcher
  def initWorkers = {
    val router = context.system.actorOf(props.withRouter(RoundRobinRouter(20, supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 2) {
      case e => log.error(s"====> exception: ${e.getClass.getName} messsage ${e.getMessage}"); Restart
    })), name = "router")
    context.watch(router)
    router
  }
  var router = initWorkers

  def receive = {
    case Terminated(routerRef) =>
      //TODO: handle termination of router (should not happen)
      log.error(s"Actor $routerRef was terminated. Restart in 5 seconds")
      context.system.scheduler.scheduleOnce(5 seconds) {
        log.info("Restart router")
        router = initWorkers
      }
    case message => router forward message
  }
}


