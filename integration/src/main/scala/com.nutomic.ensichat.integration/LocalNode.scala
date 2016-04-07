package com.nutomic.ensichat.integration

import java.io.File
import java.util.concurrent.{LinkedBlockingDeque, LinkedBlockingQueue}

import com.nutomic.ensichat.core.body.{RouteError, RouteRequest, RouteReply}
import com.nutomic.ensichat.core.interfaces.{CallbackInterface, SettingsInterface}
import com.nutomic.ensichat.core.util.Database
import com.nutomic.ensichat.core.{ConnectionHandler, Crypto, Message}
import com.nutomic.ensichat.integration.LocalNode._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scalax.file.Path

object LocalNode {

  private final val StartingPort = 21000

  object EventType extends Enumeration {
    type EventType = Value
    val MessageReceived, ConnectionsChanged, ContactsUpdated = Value
  }

  class FifoStream[A]() {
    private val queue = new LinkedBlockingQueue[Option[A]]()
    def toStream: Stream[A] = queue.take match {
      case Some(a) => Stream.cons(a, toStream)
      case None => Stream.empty
    }
    def close() = queue add None
    def enqueue(a: A) = queue.put(Option(a))
  }

}

/**
  * Runs an ensichat node on localhost.
  *
  * Received messages can be accessed through [[eventQueue]].
  *
  * @param index Number of this node. The server port is opened on port [[StartingPort]] + index.
  * @param configFolder Folder where keys and configuration should be stored.
  */
class LocalNode(val index: Int, configFolder: File) extends CallbackInterface {

  import com.nutomic.ensichat.integration.LocalNode.EventType._
  private val databaseFile = new File(configFolder, "database")
  private val keyFolder    = new File(configFolder, "keys")

  private val database = new Database(databaseFile, this)
  private val settings = new SettingsInterface {
    private var values = Map[String, Any]()
    override def get[T](key: String, default: T): T = values.get(key).map(_.asInstanceOf[T]).getOrElse(default)
    override def put[T](key: String, value: T): Unit = values += (key -> value.asInstanceOf[Any])
  }

  val crypto            = new Crypto(settings, keyFolder)
  val connectionHandler = new ConnectionHandler(settings, database, this, crypto, 0, port)
  val eventQueue        = new FifoStream[(EventType.EventType, Option[Message])]()

  configFolder.mkdirs()
  keyFolder.mkdirs()
  settings.put(SettingsInterface.KeyAddresses, "")
  Await.result(connectionHandler.start(), Duration.Inf)

  def port = StartingPort + index

  def stop(): Unit = {
    connectionHandler.stop()
    Path(configFolder).deleteRecursively()
  }

  def onMessageReceived(msg: Message): Unit = {
    eventQueue.enqueue((EventType.MessageReceived, Option(msg)))
  }

  def onConnectionsChanged(): Unit =
    eventQueue.enqueue((EventType.ConnectionsChanged, None))

  def onContactsUpdated(): Unit =
    eventQueue.enqueue((EventType.ContactsUpdated, None))

}
