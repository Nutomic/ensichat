package com.nutomic.ensichat.util

import java.io.File
import java.util.concurrent.CountDownLatch

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.test.AndroidTestCase
import android.test.mock.MockContext
import com.nutomic.ensichat.aodvv2.AddressTest
import com.nutomic.ensichat.aodvv2.MessageTest._
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
    val msg1 = Database.getMessages(m1.Header.Origin, 1)
    assertEquals(1, msg1.size)

    val msg2 = Database.getMessages(m1.Header.Origin, 3)
    assertEquals(2, msg2.size)
  }

  def testMessageOrder(): Unit = {
    val msg = Database.getMessages(m1.Header.Target, 1)
    assertTrue(msg.contains(m3))
  }

  def testMessageSelect(): Unit = {
    val msg = Database.getMessages(m1.Header.Target, 2)
    assertTrue(msg.contains(m1))
    assertTrue(msg.contains(m3))
  }

  def testAddContact(): Unit = {
    Database.addContact(AddressTest.a1)
    assertTrue(Database.isContact(AddressTest.a1))
    val contacts = Database.getContacts
    assertEquals(1, contacts.size)
    contacts.foreach{assertEquals(AddressTest.a1, _)}
  }

  def testAddContactCallback(): Unit = {
    val latch = new CountDownLatch(1)
    Database.runOnContactsUpdated(() => {
      latch.countDown()
    })
    Database.addContact(AddressTest.a1)
    latch.await()
  }

}
