package vcmd.clients

import org.productivity.java.syslog4j._
import org.productivity.java.syslog4j.impl.net.tcp.TCPNetSyslogConfig
/**
 * PooledTCPNetSyslog
 * TCPNetSyslog
 * AbstractSyslogWriter
 */
object SyslogClient extends App {

  def measure[T](callback: => T): (Long, T) = {
    val start = System.currentTimeMillis
    val res = callback
    val elapsed = System.currentTimeMillis - start
    (elapsed, res)
  }

  val config = new TCPNetSyslogConfig()//new PooledTCPNetSyslogConfig()
  config.setPort(1234) 
  config.setHost("localhost")
  val instance = Syslog.createInstance("pooledTcp", config);
  val to = 1000000
  val (elapsed, _) = measure {
    1 to to foreach (i => instance.info(s"$i"))
  }

  println(s"===========================================>total sent: $to, elapsed $elapsed ms, tps ${to / elapsed * 1000}")
}