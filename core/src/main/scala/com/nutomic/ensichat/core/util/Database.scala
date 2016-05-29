package com.nutomic.ensichat.core.util

import java.io.File
import java.util.Date

import com.nutomic.ensichat.core.body.Text
import com.nutomic.ensichat.core.header.ContentHeader
import com.nutomic.ensichat.core.interfaces.CallbackInterface
import com.nutomic.ensichat.core.{Address, Message, User}
import com.typesafe.scalalogging.Logger
import slick.driver.H2Driver.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration


/**
 * Handles persistent data storage.
 *
 * @param path The database file.
 */
class Database(path: File, callbackInterface: CallbackInterface) {

  private val logger = Logger(this.getClass)

  private class Messages(tag: Tag) extends Table[Message](tag, "MESSAGES") {
    def id        = primaryKey("id", (origin, messageId))
    def origin    = column[String]("origin")
    def target    = column[String]("target")
    def messageId = column[Long]("message_id")
    def text      = column[String]("text")
    def date      = column[Long]("date")
    def * = (origin, target, messageId, text, date).<> [Message, (String, String, Long, String, Long)]( { tuple =>
      val header = new ContentHeader(new Address(tuple._1),
        new Address(tuple._2),
        -1,
        Text.Type,
        Some(tuple._3),
        Some(new Date(tuple._5)))
      val body = new Text(tuple._4)
      new Message(header, body)
    }, { message =>
      Option((message.header.origin.toString(), message.header.target.toString(),
        message.header.messageId.get, message.body.asInstanceOf[Text].text,
        message.header.time.get.getTime))
    })
  }
  private val messages = TableQuery[Messages]

  private class Contacts(tag: Tag) extends Table[User](tag, "CONTACTS") {
    def address = column[String]("address", O.PrimaryKey)
    def name    = column[String]("name")
    def status  = column[String]("status")
    def wrappedAddress = address.<> [Address, String](new Address(_), a => Option(a.toString()))
    def * = (wrappedAddress, name, status) <> (User.tupled, User.unapply)
  }
  private val contacts = TableQuery[Contacts]

  private val db = Database.forURL("jdbc:h2:" + path.getAbsolutePath, driver = "org.h2.Driver")

  // Create tables if database doesn't exist.
  {
    // H2 appends a .mv.db suffix to the path which we can't change, so we have to check that file.
    val dbFile = new File(path.getAbsolutePath + ".mv.db")
    if (!dbFile.exists()) {
      logger.info("Database does not exist, creating tables")
      Await.result(db.run((messages.schema ++ contacts.schema).create), Duration.Inf)
    }
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

}
