package com.nutomic.ensichat.util

import java.util.concurrent.CountDownLatch

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.test.{AndroidTestCase, mock}
import com.nutomic.ensichat.protocol.UserTest
import com.nutomic.ensichat.protocol.messages.ContentHeaderTest._
import com.nutomic.ensichat.protocol.messages.MessageTest._
import com.nutomic.ensichat.protocol.messages.{ContentHeader, CryptoData}
import com.nutomic.ensichat.util.Database.OnContactsUpdatedListener
import junit.framework.Assert._

object DatabaseTest {

  /**
   * Provides a temporary database file that can be deleted with [[MockContext#deleteDbFile]].
   *
   * Does not work if multiple db files are opened!
   */
  class MockContext(context: Context) extends mock.MockContext {
    private var dbFile: String = _
    override def openOrCreateDatabase(file: String, mode: Int, factory:
    SQLiteDatabase.CursorFactory, errorHandler: DatabaseErrorHandler): SQLiteDatabase = {
      dbFile = file + "-test"
      context.openOrCreateDatabase(dbFile, mode, factory, errorHandler)
    }
    def deleteDbFile() = context.deleteDatabase(dbFile)
  }

}

class DatabaseTest extends AndroidTestCase {

  private lazy val context = new DatabaseTest.MockContext(getContext)

  private lazy val database = new Database(context)

  override def setUp(): Unit = {
    database.onMessageReceived(m1)
    database.onMessageReceived(m2)
    database.onMessageReceived(m3)
  }

  override def tearDown(): Unit = {
    super.tearDown()
    database.close()
    context.deleteDbFile()
  }

  def testMessageCount(): Unit = {
    val msg1 = database.getMessages(m1.header.origin, 1)
    assertEquals(1, msg1.size)

    val msg2 = database.getMessages(m1.header.origin, 3)
    assertEquals(2, msg2.size)
  }

  def testMessageOrder(): Unit = {
    val msg = database.getMessages(m1.header.target, 1)
    assertTrue(msg.contains(m3))
  }

  def testMessageSelect(): Unit = {
    val msg = database.getMessages(m1.header.target, 2)
    assertTrue(msg.contains(m1))
    assertTrue(msg.contains(m3))
  }

  def testMessageFields(): Unit = {
    val msg = database.getMessages(m3.header.target, 1).firstKey
    val header = msg.header.asInstanceOf[ContentHeader]

    assertEquals(h3.origin, header.origin)
    assertEquals(h3.target, header.target)
    assertEquals(-1, msg.header.seqNum)
    assertEquals(h3.contentType, header.contentType)
    assertEquals(h3.messageId, header.messageId)
    assertEquals(new CryptoData(None, None), msg.crypto)
    assertEquals(m3.body, msg.body)
  }

  def testAddContact(): Unit = {
    database.addContact(UserTest.u1)
    val contacts = database.getContacts
    assertEquals(1, contacts.size)
    assertEquals(Some(UserTest.u1), database.getContact(UserTest.u1.address))
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
    assertTrue(database.getContact(UserTest.u1.address).isEmpty)
    val c = database.getContact(UserTest.u2.address)
    assertTrue(c.nonEmpty)
    assertEquals(Some(UserTest.u2), c)
  }

}
