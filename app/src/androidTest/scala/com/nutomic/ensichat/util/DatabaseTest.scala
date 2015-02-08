package com.nutomic.ensichat.util

import java.util.concurrent.CountDownLatch

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.test.AndroidTestCase
import android.test.mock.MockContext
import com.nutomic.ensichat.protocol.UserTest
import com.nutomic.ensichat.protocol.messages.MessageTest._
import com.nutomic.ensichat.util.Database.OnContactsUpdatedListener
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

  private var database: Database = _

  override def setUp(): Unit = {
    database = new Database(new TestContext(getContext))
    database.onMessageReceived(m1)
    database.onMessageReceived(m2)
    database.onMessageReceived(m3)
  }

  override def tearDown(): Unit = {
    super.tearDown()
    database.close()
    getContext.deleteDatabase(dbFile)
  }

  def testMessageCount(): Unit = {
    val msg1 = database.getMessages(m1.Header.Origin, 1)
    assertEquals(1, msg1.size)

    val msg2 = database.getMessages(m1.Header.Origin, 3)
    assertEquals(2, msg2.size)
  }

  def testMessageOrder(): Unit = {
    val msg = database.getMessages(m1.Header.Target, 1)
    assertTrue(msg.contains(m3))
  }

  def testMessageSelect(): Unit = {
    val msg = database.getMessages(m1.Header.Target, 2)
    assertTrue(msg.contains(m1))
    assertTrue(msg.contains(m3))
  }

  def testAddContact(): Unit = {
    database.addContact(UserTest.u1)
    val contacts = database.getContacts
    assertEquals(1, contacts.size)
    assertEquals(Some(UserTest.u1), database.getContact(UserTest.u1.Address))
  }

  def testAddContactCallback(): Unit = {
    val latch = new CountDownLatch(1)
    database.runOnContactsUpdated(new OnContactsUpdatedListener {
      override def onContactsUpdated() = latch.countDown()
    })
    database.addContact(UserTest.u1)
    latch.await()
  }
  
  def testGetContact(): Unit = {
    database.addContact(UserTest.u2)
    assertTrue(database.getContact(UserTest.u1.Address).isEmpty)
    val c = database.getContact(UserTest.u2.Address)
    assertTrue(c.nonEmpty)
    assertEquals(Some(UserTest.u2), c)
  }

}
