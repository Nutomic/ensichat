package com.nutomic.ensichat.core.util

import java.io.File
import java.sql.DriverManager
import java.util.Date

import com.nutomic.ensichat.core.body.Text
import com.nutomic.ensichat.core.header.ContentHeader
import com.nutomic.ensichat.core.interfaces.{CallbackInterface, SettingsInterface}
import com.nutomic.ensichat.core.{Address, Message, User}
import com.typesafe.scalalogging.Logger
import org.joda.time
import slick.driver.H2Driver.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration


/**
 * Handles persistent data storage.
 *
 * @param path The database file.
 */
class Database(path: File, settings: SettingsInterface, callbackInterface: CallbackInterface) {

  private val logger = Logger(this.getClass)

  private val DatabaseVersionKey = "database_version"
  private val DatabaseVersion = 2

  private val DatabasePath = "jdbc:h2:" + path.getAbsolutePath + ";DATABASE_TO_UPPER=false"

  private class Messages(tag: Tag) extends Table[Message](tag, "MESSAGES") {
    def id        = primaryKey("id", (origin, messageId))
    def origin    = column[String]("origin")
    def target    = column[String]("target")
    def messageId = column[Long]("message_id")
    def text      = column[String]("text")
    def date      = column[Long]("date")
    def tokens    = column[Int]("tokens")
    def * = (origin, target, messageId, text, date, tokens) <> [Message, (String, String, Long, String, Long, Int)]( {
      tuple =>
        val header = new ContentHeader(new Address(tuple._1),
          new Address(tuple._2),
          -1,
          Text.Type,
          Some(tuple._3),
          Some(new Date(tuple._5)),
          tuple._6)
        val body = new Text(tuple._4)
        new Message(header, body)
    }, message =>
      Option((message.header.origin.toString(), message.header.target.toString(),
        message.header.messageId.get, message.body.asInstanceOf[Text].text,
        message.header.time.get.getTime, message.header.tokens))
    )
  }
  private val messages = TableQuery[Messages]

  private class Contacts(tag: Tag) extends Table[User](tag, "CONTACTS") {
    def address = column[String]("address", O.PrimaryKey)
    def name    = column[String]("name")
    def status  = column[String]("status")
    def wrappedAddress = address <> [Address, String](new Address(_), a => Option(a.toString))
    def * = (wrappedAddress, name, status) <> (User.tupled, User.unapply)
  }
  private val contacts = TableQuery[Contacts]

  private class KnownDevices(tag: Tag) extends Table[(Address, time.Duration)](tag, "KNOWN_DEVICES") {
    def address                 = column[String]("address", O.PrimaryKey)
    def totalConnectionSeconds  = column[Long]("total_connection_seconds")
    def * = (address, totalConnectionSeconds) <> [(Address, time.Duration), (String, Long)](
      tuple => (new Address(tuple._1), time.Duration.standardSeconds(tuple._2)),
      tuple => Option((tuple._1.toString, tuple._2.getStandardSeconds)))
  }
  private val knownDevices = TableQuery[KnownDevices]

  private val db = Database.forURL(DatabasePath, driver = "org.h2.Driver")

  createDatabase()
  upgradeDatabase()

  /**
    * Creates a new database file at [[DatabasePath]] if it doesn't already exist.
    */
  private def createDatabase(): Unit = {
    // H2 appends a .mv.db suffix to the path which we can't change, so we have to check that file.
    val dbFile = new File(path.getAbsolutePath + ".mv.db")
    if (dbFile.exists())
      return

    logger.info("Database does not exist, creating tables")
    val query = (messages.schema ++ contacts.schema ++ knownDevices.schema).create
    Await.result(db.run(query), Duration.Inf)
    settings.put(DatabaseVersionKey, DatabaseVersion)
  }

  /**
    * Upgrades database to new version if needed, based on [[DatabaseVersion]].
    */
  private def upgradeDatabase(): Unit = {
    val oldVersion = settings.get(DatabaseVersionKey, 0)
    if (oldVersion == DatabaseVersion)
      return

    logger.info(s"Upgrading database from version $oldVersion to $DatabaseVersion")
    val connection = DriverManager.getConnection(DatabasePath)
    if (oldVersion <= 2) {
      connection.createStatement().executeUpdate("ALTER TABLE MESSAGES ADD COLUMN (tokens INT);")
      connection.commit()
      Await.result(db.run(knownDevices.schema.create), Duration.Inf)
    }
    connection.close()
    settings.put(DatabaseVersionKey, DatabaseVersion)
  }

  // Apparently, slick doesn't support ALTER TABLE, so we have to write raw SQL for this...
  {
  }

  def close(): Unit = {
    Await.result(db.shutdown, Duration.Inf)
  }

  /**
   * Inserts the given new message into the database.
   */
  def onMessageReceived(msg: Message): Unit = msg.body match {
    case _: Text => Await.result(db.run(messages += msg), Duration.Inf)
    case _ =>
  }

  def getMessages(address: Address): Seq[Message] = {
    val query = messages.filter { m =>
      m.origin === address.toString || m.target === address.toString
    }
    Await.result(db.run(query.result), Duration.Inf)
  }

  /**
   * Returns all contacts of this user.
   */
  def getContacts: Set[User] = {
    val f = db.run(contacts.result)
    Await.result(f, Duration.Inf).toSet
  }

  /**
   * Returns the contact with the given address if it exists.
   */
  def getContact(address: Address): Option[User] = {
    val query = contacts.filter { c =>
      c.address === address.toString
    }
    Await.result(db.run(query.result), Duration.Inf).headOption
  }

  /**
   * Inserts the user as a new contact.
   */
  def addContact(contact: User): Unit = {
    Await.result(db.run(contacts += contact), Duration.Inf)
    callbackInterface.onContactsUpdated()
  }

  /**
   * Updates an existing contact.
   */
  def updateContact(contact: User): Unit = {
    assert(getContact(contact.address).nonEmpty)
    Await.result(db.run(contacts.insertOrUpdate(contact)), Duration.Inf)
    callbackInterface.onContactsUpdated()
  }

  def insertOrUpdateKnownDevice(address: Address, connectionTime: time.Duration): Unit = {
    val query = knownDevices.insertOrUpdate((address, connectionTime))
    Await.result(db.run(query), Duration.Inf)
  }

  /**
    * Returns neighbors sorted by connection time, according to [[KnownDevices]].
    */
  def pickLongestConnectionDevice(connections: Set[Address]): List[Address] = {
    val map = Await.result(db.run(knownDevices.result), Duration.Inf).toMap
    connections
      .toList
      .sortBy { c =>
        val duration = map.get(c)
        duration.map(_.getMillis).getOrElse(0L)
      }
      .reverse
  }

  def updateMessageForwardingTokens(message: Message, tokens: Int): Unit = {
    val query = messages.filter { c =>
        c.origin === message.header.origin.toString &&
        c.messageId === message.header.messageId
      }
      .map(_.tokens)
      .update(tokens)
    Await.result(db.run(query), Duration.Inf)
  }

}
