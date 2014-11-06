package com.nutomic.ensichat.messages

import java.util.Date

import android.content.{ContentValues, Context}
import android.database.Cursor
import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import com.nutomic.ensichat.bluetooth.Device

import scala.collection.SortedSet
import scala.collection.immutable.TreeSet

object MessageStore {

  private val DatabaseName = "message_store.db"

  private val DatabaseVersion = 1

  private val DatabaseCreate = "CREATE TABLE messages(" +
    "_id integer primary key autoincrement," +
    "sender string not null," +
    "receiver string not null," +
    "text blob not null," +
    "date integer not null);" // Unix timestamp of message.

}

/**
 * Stores all messages in SQL database.
 */
class MessageStore(context: Context) extends SQLiteOpenHelper(context, MessageStore.DatabaseName,
                                                              null, MessageStore.DatabaseVersion) {

  private val Tag = "MessageStore"

  override def onCreate(db: SQLiteDatabase): Unit = {
    db.execSQL(MessageStore.DatabaseCreate)
  }

  /**
   * Returns the count last messages for device.
   */
  def getMessages(device: Device.ID, count: Int): SortedSet[Message] = {
    val c: Cursor = getReadableDatabase.query(true,
      "messages", Array("sender", "receiver", "text", "date"),
      "sender = ? OR receiver = ?", Array(device.toString, device.toString),
      null, null, "date DESC", count.toString)
    var messages: SortedSet[Message] = new TreeSet[Message]()(Message.Ordering)
    while (c.moveToNext()) {
      val m: TextMessage = new TextMessage(
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
      val cv: ContentValues =  new ContentValues()
      cv.put("sender", msg.sender.toString)
      cv.put("receiver", msg.receiver.toString)
      // toString used as workaround for compile error with Long.
      cv.put("date", msg.date.getTime.toString)
      cv.put("text", msg.text)
      getWritableDatabase.insert("messages", null, cv)
    case msg: DeviceInfoMessage => // Never stored.
  }

  override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = {
  }

}