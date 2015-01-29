package com.nutomic.ensichat.util

import java.util.Date

import android.content.{ContentValues, Context}
import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import android.util.Log
import com.nutomic.ensichat.protocol._
import com.nutomic.ensichat.protocol.messages._

import scala.collection.SortedSet
import scala.collection.immutable.TreeSet

object Database {

  private val DatabaseName = "message_store.db"

  private val DatabaseVersion = 1

  private val CreateMessagesTable = "CREATE TABLE messages(" +
    "_id integer primary key autoincrement," +
    "origin text not null," +
    "target text not null," +
    "text text not null," +
    "date integer not null);" // Unix timestamp of message.

  private val CreateContactsTable = "CREATE TABLE contacts(" +
    "_id integer primary key autoincrement," +
    "address text not null," +
    "name text not null)"

}

/**
 * Stores all messages and contacts in SQL database.
 */
class Database(context: Context) extends SQLiteOpenHelper(context, Database.DatabaseName,
                                                          null, Database.DatabaseVersion) {

  private var contactsUpdatedListeners = Set[() => Unit]()

  override def onCreate(db: SQLiteDatabase): Unit = {
    db.execSQL(Database.CreateContactsTable)
    db.execSQL(Database.CreateMessagesTable)
  }

  /**
   * Returns the count last messages for device.
   */
  def getMessages(address: Address, count: Int): SortedSet[Message] = {
    val c = getReadableDatabase.query(true,
      "messages", Array("origin", "target", "text", "date"),
      "origin = ? OR target = ?", Array(address.toString, address.toString),
      null, null, "date DESC", count.toString)
    var messages = new TreeSet[Message]()(Message.Ordering)
    while (c.moveToNext()) {
      val header = new MessageHeader(
        Text.Type,
        -1,
        new Address(c.getString(c.getColumnIndex("origin"))),
        new Address(c.getString(c.getColumnIndex("target"))),
        -1,
        -1)
      val body = new Text(new String(c.getString(c.getColumnIndex ("text"))),
        new Date(c.getLong(c.getColumnIndex("date"))))
      messages += new Message(header, body)
    }
    c.close()
    messages
  }

  /**
   * Inserts the given new message into the database.
   */
  def addMessage(message: Message): Unit = message.Body match {
    case text: Text =>
      val cv =  new ContentValues()
      cv.put("origin", message.Header.Origin.toString)
      cv.put("target", message.Header.Target.toString)
      // toString used as workaround for compile error with Long.
      cv.put("date", text.time.getTime.toString)
      cv.put("text", text.text)
      getWritableDatabase.insert("messages", null, cv)
    case _: ConnectionInfo | _: RequestAddContact | _: ResultAddContact | _: UserName =>
      // Never stored.
  }

  /**
   * Returns all contacts of this user.
   */
  def getContacts: Set[User] = {
    val c = getReadableDatabase.query(true, "contacts", Array("address", "name"), "", Array(),
      null, null, null, null)
    var contacts = Set[User]()
    while (c.moveToNext()) {
      contacts += new User(new Address(c.getString(c.getColumnIndex("address"))),
                              c.getString(c.getColumnIndex("name")))
    }
    c.close()
    contacts
  }

  /**
   * Returns the contact with the given address if it exists.
   */
  def getContact(address: Address): Option[User] = {
    val c = getReadableDatabase.query(true, "contacts", Array("address", "name"), "address = ?",
      Array(address.toString), null, null, null, null)
    if (c.getCount != 0) {
      c.moveToNext()
      val s = Some(new User(new Address(c.getString(c.getColumnIndex("address"))),
        c.getString(c.getColumnIndex("name"))))
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
    cv.put("address", contact.Address.toString)
    cv.put("name", contact.Name.toString)
    getWritableDatabase.insert("contacts", null, cv)
    contactsUpdatedListeners.foreach(_())
  }
  
  def changeContactName(contact: User): Unit = {
    val cv = new ContentValues()
    cv.put("name", contact.Name.toString)
    getWritableDatabase.update("contacts", cv, "address = ?", Array(contact.Address.toString))
    contactsUpdatedListeners.foreach(_())
  }

  /**
   * Pass a callback that is called whenever a new contact is added.
   */
  def runOnContactsUpdated(l: () => Unit): Unit = contactsUpdatedListeners += l

  override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = {
  }

}
