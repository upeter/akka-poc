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

  def validateEcho(in: String)(resp: String) = {

    val expected = s"echo: $in"
    val equal = expected.startsWith(resp)
    if (!equal) {
      println(s"expected $expected ${expected.size}")
      println(s"received $resp ${resp.size}")
      //assert(equal)
    }
    equal

  }

  def validateResponse(resp: String) = {
    val valid = resp.startsWith("1")
    if (!valid) {
      println(s"expected response starting with 1 but received $resp")
    }
    valid

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
        log.debug("init ok: -> become receive")
        context.become(defaultHandling)
      })
  }

  def defaultHandling: Receive = {
    case msg @ RawMessage(_) => send(msg)
  }

  def reconnect: Receive = {
    case msg: InitMessage => {
      log.debug(s"retry message=[${msg}]...")
      send(msg, onSuccess = (req, resp) => {
        log.debug("retry ok: unstashAll -> become receive")
        unstashAll
        context.become(defaultHandling)
      })
    }
    case message => stash
  }

  override def postStop = {
    log.info(s"Stopping actor ${self}")
    riskShieldSender.disconnect
  }

  //******************************************
  //Private helper methods
  //******************************************

  private def doSend[T](fun: => T): Try[T] = {
   def preHandleFailure(e: Throwable) = {
      val reason = s"${e.getClass().getSimpleName()} occured ${e.getMessage()}. Close connection."
      log.error(reason)
      riskShieldSender.disconnect
      riskShieldSender = initSender
    }

    val result = Try(fun)
    if (result.isFailure) {
      val Failure(e) = result
      preHandleFailure(e)
    } else {
      context.system.eventStream.publish(MessageSent(self))
    }
    result
  }

  private def onFailureDefault(msg: InitMessage) = {
    log.error(s"Failure occured. Schedule retry in ${riskShieldRetryInterval} millis.")
    scheduler.scheduleOnce(riskShieldRetryInterval milliseconds) {
      self ! msg
    }
    log.debug("send failed: -> become reconnect")
    context.become(reconnect)
  }

  private def onSuccessDefault(req: String, resp: String): Unit = { /*empty*/}

  private def send(msg: Message, onSuccess: (String, String) => Unit = onSuccessDefault, onFailure: InitMessage => Unit = onFailureDefault) = {
    msg match {
      case raw @ RawMessage(msg) =>
        val res = for {
          resp <- doSend(riskShieldSender.sendAndReceive(msg))
          if validateResponse(resp)
        } yield resp
        handleResult(res, InitMessage(msg, "Metadata"))
      case init @ InitMessage(msg, meta) => {
        val res = for {
          empty <- doSend(riskShieldSender.send(meta))
          headerResp <- doSend(riskShieldSender.sendAndReceive(msg))
          resp <- doSend(riskShieldSender.read())
          if validateResponse(resp)
        } yield resp
        handleResult(res, init)

      }
      case unkown => throw new IllegalStateException(s"Invalid message type: ${unkown.getClass().getName()}")

    }
    def handleResult(res: Try[String], reqMsg: InitMessage) = {
      res match {
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

  lazy private val in = new BufferedReader(new InputStreamReader(
    riskShieldSocket.getInputStream(), "utf-8"));

  lazy private val out = new PrintWriter(new OutputStreamWriter(riskShieldSocket.getOutputStream(), "utf-8"), true);

  def sendAndReceive(msg: String): String = {
    send(msg)
    read()
  }

  def read(times: Int = 1): String = {
    Option(in.readLine()) match {
      case Some(resp) => resp
      case None =>
        throw new SocketReadException("No response received")
    }

  }

  def send(msg: String): Unit = {
    out.println(msg)
    out.flush
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
    val router = context.system.actorOf(props.withRouter(RoundRobinRouter(30, supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 2) {
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


