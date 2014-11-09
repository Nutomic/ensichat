package com.nutomic.ensichat.messages

import java.io.File

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.test.AndroidTestCase
import android.test.mock.MockContext
import com.nutomic.ensichat.messages.MessageTest._
import junit.framework.Assert._

class MessageStoreTest extends AndroidTestCase {

  private class TestContext(context: Context) extends MockContext {
    override def openOrCreateDatabase(file: String, mode: Int, factory:
    SQLiteDatabase.CursorFactory, errorHandler: DatabaseErrorHandler): SQLiteDatabase = {
      dbFile = file + "-test"
      context.openOrCreateDatabase(dbFile, mode, factory, errorHandler)
    }
  }

  private var dbFile: String = _

  private var MessageStore: MessageStore = _

  override def setUp(): Unit = {
    MessageStore = new MessageStore(new TestContext(getContext))
    MessageStore.addMessage(m1)
    MessageStore.addMessage(m2)
    MessageStore.addMessage(m3)
  }

  override def tearDown(): Unit = {
    super.tearDown()
    new File(dbFile).delete()
  }

  def testCount(): Unit = {
    val msg1 = MessageStore.getMessages(m1.sender, 1)
    assertEquals(1, msg1.size)

    val msg2 = MessageStore.getMessages(m1.sender, 3)
    assertEquals(2, msg2.size)
  }

  def testOrder(): Unit = {
    val msg = MessageStore.getMessages(m1.receiver, 1)
    assertTrue(msg.contains(m3))
  }

  def testSelect(): Unit = {
    val msg = MessageStore.getMessages(m1.receiver, 2)
    assertTrue(msg.contains(m1))
    assertTrue(msg.contains(m3))
  }

}
