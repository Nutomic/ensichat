package com.nutomic.ensichat.util

import java.util.Date

import android.content.{ContentValues, Context}
import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import com.nutomic.ensichat.bluetooth.Device
import com.nutomic.ensichat.messages._

import scala.collection.SortedSet
import scala.collection.immutable.TreeSet

object Database {

  private val DatabaseName = "message_store.db"

  private val DatabaseVersion = 1

  private val CreateMessagesTable = "CREATE TABLE messages(" +
    "_id integer primary key autoincrement," +
    "sender string not null," +
    "receiver string not null," +
    "text blob not null," +
    "date integer not null);" // Unix timestamp of message.

  private val CreateContactsTable = "CREATE TABLE contacts(" +
    "_id integer primary key autoincrement," +
    "device_id string not null," +
    "name string not null)"

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
  def getMessages(device: Device.ID, count: Int): SortedSet[Message] = {
    val c = getReadableDatabase.query(true,
      "messages", Array("sender", "receiver", "text", "date"),
      "sender = ? OR receiver = ?", Array(device.toString, device.toString),
      null, null, "date DESC", count.toString)
    var messages = new TreeSet[Message]()(Message.Ordering)
    while (c.moveToNext()) {
      val m = new TextMessage(
        new Device.ID(c.getString(c.getColumnIndex("sender"))),
        new Device.ID(c.getString(c.getColumnIndex("receiver"))),
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
    case _: DeviceInfoMessage | _: RequestAddContactMessage | _: ResultAddContactMessage =>
      // Never stored.
  }

  /**
   * Returns a list of all contacts of this device.
   */
  def getContacts: Set[Device] = {
    val c = getReadableDatabase.query(true, "contacts", Array("device_id", "name"), "", Array(),
      null, null, "name DESC", null)
    var contacts = Set[Device]()
    while (c.moveToNext()) {
      contacts += new Device(new Device.ID(c.getString(c.getColumnIndex("device_id"))),
                              c.getString(c.getColumnIndex("name")), false)
    }
    c.close()
    contacts
  }

  /**
   * Returns true if a contact with the given device ID exists.
   */
  def isContact(device: Device.ID): Boolean = {
    val c = getReadableDatabase.query(true, "contacts", Array("_id"), "device_id = ?",
      Array(device.toString), null, null, null, null)
    c.getCount != 0
  }

  /**
   * Inserts the given device into contacts.
   */
  def addContact(device: Device): Unit = {
    val cv = new ContentValues()
    cv.put("device_id", device.Id.toString)
    cv.put("name", device.Name)
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
