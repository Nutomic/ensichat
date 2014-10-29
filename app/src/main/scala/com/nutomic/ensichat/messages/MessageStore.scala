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
  def getMessages(device: Device.ID, count: Int): SortedSet[TextMessage] = {
    val c: Cursor = getReadableDatabase.query(true,
      "messages", Array("sender", "receiver", "text", "date"),
      "sender = ? OR receiver = ?", Array(device.toString, device.toString),
      null, null, "date DESC", count.toString)
    var messages: SortedSet[TextMessage] = new TreeSet[TextMessage]()(TextMessage.Ordering)
    while (c.moveToNext()) {
      val m: TextMessage = new TextMessage(
        new Device.ID(c.getString(c.getColumnIndex("sender"))),
        new Device.ID(c.getString(c.getColumnIndex("receiver"))),
        new String(c.getBlob(c.getColumnIndex ("text"))),
        new Date(c.getLong(c.getColumnIndex("date"))))
      messages += m
    }
    c.close()
    messages
  }

  /**
   * Inserts the given new message into the database.
   */
  def addMessage(message: TextMessage): Unit = {
    val cv: ContentValues =  new ContentValues()
    cv.put("sender", message.sender.toString)
    cv.put("receiver", message.receiver.toString)
    cv.put("text", message.text)
    cv.put("date", message.date.getTime.toString) // toString used as workaround for compile error
    getWritableDatabase.insert("messages", null, cv)
  }

  override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = {
  }

}