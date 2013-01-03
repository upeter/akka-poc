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
import io._
object Boot extends App {
  implicit val timeout: Timeout = 4 seconds
  val system = ActorSystem("vcmd")
  val settings = Settings(system)
  import settings._
  implicit val dispatcher = system.dispatcher

  val router = system.actorOf(Props(new SyslogProcessorMasterActor(Props[RiskShieldSenderActor])))
  // val syslogListener = system.actorOf(Props(new NonBlockingSocketServer(syslogListenerPort, SyslogListener.processRequest(router))), "sysloglistener")

  val workerFactory = (socket: Socket) => new WorkerImpl(socket, SyslogDispatcher.processRequest(router))

  system.actorOf(Props(new BlockingSyslogListenerActor(new BlockingSocketServer(syslogListenerPort, workerFactory))))
}

/*
 *  router with supervised children
  val supervisor = system.actorOf(Props[RiskShieldProcessorSupervisor])
  val routeesConfig: Seq[Future[ActorRef]] = for (i <- 1 to 1) yield (supervisor ? new Props().withCreator(new RiskShieldSenderActor(host, riskShieldServerPort, readTimeout))).mapTo[ActorRef]
  val supervisedRoutees: Seq[ActorRef] = Await.result(Future.sequence(routeesConfig), 1.seconds)
  val router = system.actorOf(Props[RiskShieldProcessorSupervisor].withRouter(
    RoundRobinRouter(routees = supervisedRoutees)))

	* router that is supervisor
  val routees:Seq[ActorRef] = 1 to 1 map (i => system.actorOf(new Props().withCreator(new RiskShieldSenderActor(host, riskShieldServerPort, readTimeout)), s"actor_$i"))
  val router = system.actorOf(Props[RiskShieldProcessorSupervisor].withRouter(
    RoundRobinRouter(routees = routees.map(_.path.toString), supervisorStrategy = SupervisorStrategy.supervisorStrategy)))

* inline router 
 //  val router = system.actorOf(new Props().withCreator(new RiskShieldSenderActor(host, riskShieldServerPort, readTimeout))
  //    .withRouter(RoundRobinRouter(1, supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 2) {
  //      case e => println(s"====> exception: ${e.getClass.getName} messsage ${e.getMessage}"); Restart
  //    })), name = "router")
* 
*  */

