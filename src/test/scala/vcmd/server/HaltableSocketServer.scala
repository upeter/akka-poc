package vcmd.server

import java.net.Socket
import java.io.IOException
import java.net.ServerSocket
import java.io.BufferedReader
import java.io.InputStreamReader
import scala.concurrent.ExecutionContext

class HaltableSocketServer(port: Int, workerFactory:Socket => Worker)(implicit executer: ExecutionContext) extends Runnable {
  @volatile
  var stopped = false
  private var connections: Vector[Worker] = Vector()

  lazy val serverSocket = new ServerSocket(port)

  def run() {
    while (!stopped) {
      try {
        val clientSocket = serverSocket.accept();
        val worker = workerFactory(clientSocket)
        executer.execute(worker)
        removeClosedConections
        connections = connections :+ worker
      } catch {
        case e: IOException =>
          if (stopped) println("Server Stopped.")
          else println("Error accepting client connection", e);
      }
    }
    println("Server Stopped.");
  }

  def stop() {
    stopped = true;
    if (!serverSocket.isClosed())
      serverSocket.close();
    connections.foreach(_.close)

  }
  
  def haltConnections = connections.foreach(_.halt)
  def resumeConnections =  connections.foreach(_.resume)
  protected def removeClosedConections = {
    connections = connections filterNot(_.closed)  
  } 


}



trait Worker extends Runnable {
  def halt():Unit
  def resume():Unit
  def close():Unit
  def closed:Boolean
  
  
}


class WorkerImpl(clientSocket: Socket, handleLine:String => Any) extends Worker with Runnable {

  var mute = false;

  def resume() {
    synchronized {
      mute = false;
      notifyAll();
    }
  }

  def halt() {
    mute = true;
  }

  def closed = clientSocket.isClosed
  
  def close() {
    if (!clientSocket.isClosed) {
      clientSocket.close();
    }
  }

  def run() {
    val rd: BufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"))
    try {
      var line = rd.readLine
      while (line != null) {
        handleLine(line)
        line = rd.readLine
        while (mute) {
          println("waiting ...")
          synchronized {
            wait()
          }
          println("waiting done")
        }
      }
    } finally {
      close()
    }
  }
}
