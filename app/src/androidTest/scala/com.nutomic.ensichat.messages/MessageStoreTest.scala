package com.nutomic.ensichat.messages

import java.io.File

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.test.AndroidTestCase
import android.test.mock.MockContext
import com.nutomic.ensichat.bluetooth.Device
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
    MessageStore.addMessage(TextMessageTest.m1)
    MessageStore.addMessage(TextMessageTest.m2)
    MessageStore.addMessage(TextMessageTest.m3)
  }

  override def tearDown(): Unit = {
    super.tearDown()
    new File(dbFile).delete()
  }

  def testCount(): Unit = {
    val msg1 = MessageStore.getMessages(new Device.ID("one"), 1)
    assertEquals(1, msg1.size)

    val msg2 = MessageStore.getMessages(new Device.ID("one"), 3)
    assertEquals(2, msg2.size)
  }

  def testOrder(): Unit = {
    val msg = MessageStore.getMessages(new Device.ID("two"), 1)
    assertTrue(msg.contains(TextMessageTest.m3))
  }

  def testSelect(): Unit = {
    val msg = MessageStore.getMessages(new Device.ID("two"), 2)
    assertTrue(msg.contains(TextMessageTest.m1))
    assertTrue(msg.contains(TextMessageTest.m3))
  }

}
