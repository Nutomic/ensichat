package com.nutomic.ensichat.util

import java.io.File
import java.util.concurrent.CountDownLatch

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.test.AndroidTestCase
import android.test.mock.MockContext
import com.nutomic.ensichat.bluetooth.Device
import com.nutomic.ensichat.messages.MessageTest._
import junit.framework.Assert._

class DatabaseTest extends AndroidTestCase {

  private class TestContext(context: Context) extends MockContext {
    override def openOrCreateDatabase(file: String, mode: Int, factory:
    SQLiteDatabase.CursorFactory, errorHandler: DatabaseErrorHandler): SQLiteDatabase = {
      dbFile = file + "-test"
      context.openOrCreateDatabase(dbFile, mode, factory, errorHandler)
    }
  }

  private var dbFile: String = _

  private lazy val Database = new Database(new TestContext(getContext))

  override def setUp(): Unit = {
    Database.addMessage(m1)
    Database.addMessage(m2)
    Database.addMessage(m3)
  }

  override def tearDown(): Unit = {
    super.tearDown()
    new File(dbFile).delete()
  }

  def testMessageCount(): Unit = {
    val msg1 = Database.getMessages(m1.sender, 1)
    assertEquals(1, msg1.size)

    val msg2 = Database.getMessages(m1.sender, 3)
    assertEquals(2, msg2.size)
  }

  def testMessageOrder(): Unit = {
    val msg = Database.getMessages(m1.receiver, 1)
    assertTrue(msg.contains(m3))
  }

  def testMessageSelect(): Unit = {
    val msg = Database.getMessages(m1.receiver, 2)
    assertTrue(msg.contains(m1))
    assertTrue(msg.contains(m3))
  }

  def testAddContact(): Unit = {
    val device = new Device(m1.sender, "device", false)
    Database.addContact(device)
    assertTrue(Database.isContact(device.Id))
    val contacts = Database.getContacts
    assertEquals(1, contacts.size)
    contacts.foreach {d =>
      assertEquals(device.Name, d.Name)
      assertEquals(device.Id, d.Id)
    }
  }

  def testAddContactCallback(): Unit = {
    val device = new Device(m1.sender, "device", false)
    var latch = new CountDownLatch(1)
    Database.runOnContactsUpdated(() => {
      latch.countDown()
    })
    Database.addContact(device)
    latch.await()
  }

}
