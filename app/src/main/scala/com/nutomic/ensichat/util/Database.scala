package com.nutomic.ensichat.util

import java.util.Date

import android.content.{ContentValues, Context}
import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import com.nutomic.ensichat.aodvv2.Address
import com.nutomic.ensichat.messages._

import scala.collection.SortedSet
import scala.collection.immutable.TreeSet

object Database {

  private val DatabaseName = "message_store.db"

  private val DatabaseVersion = 1

  private val CreateMessagesTable = "CREATE TABLE messages(" +
    "_id integer primary key autoincrement," +
    "sender text not null," +
    "receiver text not null," +
    "text text not null," +
    "date integer not null);" // Unix timestamp of message.

  private val CreateContactsTable = "CREATE TABLE contacts(" +
    "_id integer primary key autoincrement," +
    "address text not null)"

}

/**
 * Stores all messages and contacts in SQL database.
 */
class Database(context: Context) extends SQLiteOpenHelper(context, Database.DatabaseName,
                                                          null, Database.DatabaseVersion) {

  private val Tag = "MessageStore"

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
      "messages", Array("sender", "receiver", "text", "date"),
      "sender = ? OR receiver = ?", Array(address.toString, address.toString),
      null, null, "date DESC", count.toString)
    var messages = new TreeSet[Message]()(Message.Ordering)
    while (c.moveToNext()) {
      val m = new TextMessage(
        new Address(c.getString(c.getColumnIndex("sender"))),
        new Address(c.getString(c.getColumnIndex("receiver"))),
        new Date(c.getLong(c.getColumnIndex("date"))),
        new String(c.getString(c.getColumnIndex ("text"))))
      messages += m
    }
    c.close()
    messages
  }

  /**
   * Inserts the given new message into the database.
   */
  def addMessage(message: Message): Unit = message match {
    case msg: TextMessage =>
      val cv =  new ContentValues()
      cv.put("sender", msg.sender.toString)
      cv.put("receiver", msg.receiver.toString)
      // toString used as workaround for compile error with Long.
      cv.put("date", msg.date.getTime.toString)
      cv.put("text", msg.text)
      getWritableDatabase.insert("messages", null, cv)
    case _: RequestAddContactMessage | _: ResultAddContactMessage =>
      // Never stored.
  }

  /**
   * Returns a list of all contacts of this device.
   */
  def getContacts: Set[Address] = {
    val c = getReadableDatabase.query(true, "contacts", Array("address"), "", Array(),
      null, null, null, null)
    var contacts = Set[Address]()
    while (c.moveToNext()) {
      contacts += new Address(c.getString(c.getColumnIndex("address")))
    }
    c.close()
    contacts
  }

  /**
   * Returns true if a contact with the given device ID exists.
   */
  def isContact(address: Address): Boolean = {
    val c = getReadableDatabase.query(true, "contacts", Array("_id"), "address = ?",
      Array(address.toString), null, null, null, null)
    c.getCount != 0
  }

  /**
   * Inserts the given device into contacts.
   */
  def addContact(address: Address): Unit = {
    val cv = new ContentValues()
    cv.put("address", address.toString)
    getWritableDatabase.insert("contacts", null, cv)
    contactsUpdatedListeners.foreach(_())
  }

  /**
   * Pass a callback that is called whenever a new contact is added.
   */
  def runOnContactsUpdated(l: () => Unit): Unit = contactsUpdatedListeners += l

  override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = {
  }

}
