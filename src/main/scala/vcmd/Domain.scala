package vcmd

sealed trait Message { def msg: String }
case class RawMessage(msg: String) extends Message
case class InitMessage(msg: String, meta: String) extends Message
case class MessageReceived
case class MessageSent
