package com.nutomic.ensichat.util

import java.util.Date

import android.content.{Intent, ContentValues, Context}
import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import android.support.v4.content.LocalBroadcastManager
import com.nutomic.ensichat.protocol.ChatService.OnMessageReceivedListener
import com.nutomic.ensichat.protocol._
import com.nutomic.ensichat.protocol.body.{Text, ResultAddContact, RequestAddContact}
import com.nutomic.ensichat.protocol.header.ContentHeader

import scala.collection.immutable.TreeSet
import scala.collection.{SortedSet, mutable}

object Database {

  val ActionContactsUpdated = "Contacts_Updated"

  private val DatabaseName = "message_store.db"

  private val DatabaseVersion = 2

  // NOTE: We could make origin/target foreign keys to contacts, but:
  // - they don't change anyway
  // - we'd have to insert the local user into contacts
  private val CreateMessagesTable = "CREATE TABLE messages(" +
    "_id INTEGER PRIMARY KEY," +
    "origin TEXT NOT NULL," +
    "target TEXT NOT NULL," +
    "message_id INT NOT NULL," +
    "text TEXT NOT NULL," +
    "date INT NOT NULL);" // Unix timestamp

  private val CreateContactsTable = "CREATE TABLE contacts(" +
    "_id INTEGER PRIMARY KEY," +
    "address TEXT NOT NULL," +
    "name TEXT NOT NULL," +
    "status TEXT NOT NULL)"

}

/**
 * Stores all messages and contacts in SQL database.
 */
class Database(context: Context)
  extends SQLiteOpenHelper(context, Database.DatabaseName, null, Database.DatabaseVersion)
  with OnMessageReceivedListener {

  override def onCreate(db: SQLiteDatabase): Unit = {
    db.execSQL(Database.CreateContactsTable)
    db.execSQL(Database.CreateMessagesTable)
  }

  /**
   * Returns the count last messages for device.
   */
  def getMessages(address: Address, count: Int): SortedSet[Message] = {
    val c = getReadableDatabase.query(true,
      "messages", Array("origin", "target", "message_id", "text", "date"),
      "origin = ? OR target = ?", Array(address.toString, address.toString),
      null, null, "date DESC", count.toString)
    var messages = new TreeSet[Message]()(Message.Ordering)
    while (c.moveToNext()) {
      val header = new ContentHeader(new Address(
        c.getString(c.getColumnIndex("origin"))),
        new Address(c.getString(c.getColumnIndex("target"))),
        -1,
        Text.Type,
        c.getLong(c.getColumnIndex("message_id")),
        new Date(c.getLong(c.getColumnIndex("date"))))
      val body = new Text(new String(c.getString(c.getColumnIndex ("text"))))
      messages += new Message(header, body)
    }
    c.close()
    messages
  }

  /**
   * Inserts the given new message into the database.
   */
  override def onMessageReceived(msg: Message): Unit = msg.body match {
    case text: Text =>
      val cv =  new ContentValues()
      val ch = msg.header.asInstanceOf[ContentHeader]
      cv.put("origin", ch.origin.toString)
      cv.put("target", ch.target.toString)
      // Need to use [[Long#toString]] because of https://issues.scala-lang.org/browse/SI-2991
      cv.put("message_id", ch.messageId.toString)
      cv.put("date", ch.time.getTime.toString)
      cv.put("text", text.text)
      getWritableDatabase.insert("messages", null, cv)
    case _: RequestAddContact | _: ResultAddContact =>
      // Never stored.
  }

  /**
   * Returns all contacts of this user.
   */
  def getContacts: Set[User] = {
    val c = getReadableDatabase.query(true, "contacts", Array("address", "name", "status"), "", Array(),
      null, null, null, null)
    var contacts = Set[User]()
    while (c.moveToNext()) {
      contacts += new User(new Address(c.getString(c.getColumnIndex("address"))),
                           c.getString(c.getColumnIndex("name")),
                           c.getString(c.getColumnIndex("status")))
    }
    c.close()
    contacts
  }

  /**
   * Returns the contact with the given address if it exists.
   */
  def getContact(address: Address): Option[User] = {
    val c = getReadableDatabase.query(true, "contacts", Array("address", "name", "status"), "address = ?",
      Array(address.toString), null, null, null, null)
    if (c.getCount != 0) {
      c.moveToNext()
      val s = Option(new User(new Address(c.getString(c.getColumnIndex("address"))),
                              c.getString(c.getColumnIndex("name")),
                              c.getString(c.getColumnIndex("status"))))
      c.close()
      s
    } else {
      c.close()
      None
    }
  }

  /**
   * Inserts the given device into contacts.
   */
  def addContact(contact: User): Unit = {
    val cv = new ContentValues()
    cv.put("address", contact.address.toString)
    cv.put("name", contact.name)
    cv.put("status", contact.status)
    getWritableDatabase.insert("contacts", null, cv)
    contactsUpdated()
  }
  
  def updateContact(contact: User): Unit = {
    val cv = new ContentValues()
    cv.put("name", contact.name)
    cv.put("status", contact.status)
    getWritableDatabase.update("contacts", cv, "address = ?", Array(contact.address.toString))
    contactsUpdated()
  }

  private def contactsUpdated(): Unit = {
    LocalBroadcastManager.getInstance(context)
      .sendBroadcast(new Intent(Database.ActionContactsUpdated))
  }

  override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = {
    if (oldVersion < 2) {
      db.execSQL("ALTER TABLE contacts ADD COLUMN status TEXT")
      val cv = new ContentValues()
      cv.put("status", "")
      db.update("contacts", cv, null, null)
    }
  }

}
