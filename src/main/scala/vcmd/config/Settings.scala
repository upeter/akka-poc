package vcmd.config
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import scala.concurrent.duration.Duration
import com.typesafe.config.Config
import java.util.concurrent.TimeUnit

class SettingsImpl(config: Config) extends Extension {
  val syslogListenerPort: Int = config.getInt("vcmd.syslog.listener.port")
  val riskShieldServerPort: Int =
    config.getInt("vcmd.risk.shield.server.port")
  val riskShieldServerHost: String =
    config.getString("vcmd.risk.shield.server.host")
  val riskShieldServerConnectTimeout: Int =
    config.getInt("vcmd.risk.shield.server.connect.timeout")
  val riskShieldServerReadTimeout: Int =
    config.getInt("vcmd.risk.shield.server.read.timeout")

}
object Settings extends ExtensionId[SettingsImpl] with ExtensionIdProvider {

  override def lookup = Settings

  override def createExtension(system: ExtendedActorSystem) =
    new SettingsImpl(system.settings.config)
}


