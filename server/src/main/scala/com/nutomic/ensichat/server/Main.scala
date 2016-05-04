package com.nutomic.ensichat.server

import java.io.File
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

import com.nutomic.ensichat.core.body.Text
import com.nutomic.ensichat.core.interfaces.{CallbackInterface, SettingsInterface}
import com.nutomic.ensichat.core.util.Database
import com.nutomic.ensichat.core.{ConnectionHandler, Crypto, Message}
import com.typesafe.scalalogging.Logger
import scopt.OptionParser

object Main extends App with CallbackInterface {

  private val logger = Logger(this.getClass)

  private val ConfigFolder = Paths.get("").toFile.getAbsoluteFile
  private val ConfigFile   = new File(ConfigFolder, "config.properties")
  private val DatabaseFile = new File(ConfigFolder, "database")
  private val KeyFolder    = new File(ConfigFolder, "keys")

  private lazy val settings          = new Settings(ConfigFile)
  private lazy val crypto            = new Crypto(settings, KeyFolder)
  private lazy val database          = new Database(DatabaseFile, this)
  private lazy val connectionHandler = new ConnectionHandler(settings, database, this, crypto, 7)

  init()

  /**
   * Initializes the app, parses command line parameters.
   *
   * See build.gradle for information about passing command line parameters from gradle.
   */
  private def init(): Unit = {
    ConfigFolder.mkdirs()
    KeyFolder.mkdirs()
    sys.addShutdownHook(connectionHandler.stop())

    val parser = new OptionParser[Config]("ensichat") {
      head("ensichat")
      opt[String]('n', "name") action { (x, c) =>
        c.copy(name = Option(x))
      } text "the username for this node (optional)"
      opt[String]('s', "status") action { (x, c) =>
        c.copy(status = Option(x))
      } text "the status line (optional)"
      help("help") text "prints this usage text"
    }

    parser.parse(args, Config()).foreach { config =>
      config.name.foreach(settings.put(SettingsInterface.KeyUserName, _))
      config.status.foreach(settings.put(SettingsInterface.KeyUserStatus, _))
      run()
    }
  }

  private def run(): Unit = {
    connectionHandler.start()

    // Keep alive and print logs
    while (true) {
      Thread.sleep(TimeUnit.SECONDS.toMillis(1))
    }
  }

  def onMessageReceived(msg: Message): Unit = {
    if (msg.header.target != crypto.localAddress)
      return

    msg.body match {
      case text: Text =>
        val address = msg.header.origin
        val name = connectionHandler.getUser(address).name
        connectionHandler.sendTo(address, new Text("Hello " + name))
        logger.info("Received text: " + text.text)
      case _ =>
        logger.info("Received msg: " + msg.body)
    }
  }

  def onConnectionsChanged(): Unit = {}

  def onContactsUpdated(): Unit = {}

}
