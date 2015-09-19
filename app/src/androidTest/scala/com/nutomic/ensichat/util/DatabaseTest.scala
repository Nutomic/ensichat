package com.nutomic.ensichat.util

import java.util.concurrent.CountDownLatch

import android.content._
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.support.v4.content.LocalBroadcastManager
import android.test.AndroidTestCase
import com.nutomic.ensichat.protocol.MessageTest._
import com.nutomic.ensichat.protocol.body.CryptoData
import com.nutomic.ensichat.protocol.header.ContentHeader
import com.nutomic.ensichat.protocol.header.ContentHeaderTest._
import com.nutomic.ensichat.protocol.{Address, Message, UserTest}
import junit.framework.Assert._

import scala.collection.SortedSet
import scala.collection.immutable.TreeSet

object DatabaseTest {

  /**
   * Provides a temporary database file that can be deleted easily.
   */
  class DatabaseContext(context: Context) extends ContextWrapper(context) {
    private val dbFile = "database-test.db"
    override def openOrCreateDatabase(file: String, mode: Int, factory:
    SQLiteDatabase.CursorFactory, errorHandler: DatabaseErrorHandler): SQLiteDatabase = {
      context.openOrCreateDatabase(dbFile, mode, factory, errorHandler)
    }
    def deleteDbFile() = context.deleteDatabase(dbFile)
  }

}

class DatabaseTest extends AndroidTestCase {

  private lazy val context = new DatabaseTest.DatabaseContext(getContext)

  private lazy val database = new Database(context)

  override def setUp(): Unit = {
    super.setUp()
    database.onMessageReceived(m1)
    database.onMessageReceived(m2)
    database.onMessageReceived(m3)
  }

  override def tearDown(): Unit = {
    super.tearDown()
    database.close()
    context.deleteDbFile()
  }

  /**
   * Calls [[Database.getMessagesCursor]] with parameters and converts the result to sorted set.
   */
  private def getMessages(address: Address): SortedSet[Message] = {
    val c = database.getMessagesCursor(address)
    var messages = new TreeSet[Message]()(Message.Ordering)
    while (c.moveToNext()) {
      messages += Database.messageFromCursor(c)
    }
    c.close()
    messages
  }

  def testMessageOrder(): Unit = {
    val msg = getMessages(m1.header.target).firstKey
    assertEquals(m1.body, msg.body)
  }

  def testMessageSelect(): Unit = {
    val msg = getMessages(m1.header.target)
    assertTrue(msg.contains(m1))
    assertFalse(msg.contains(m2))
    assertTrue(msg.contains(m3))
  }

  def testMessageFields(): Unit = {
    val msg = getMessages(m2.header.target).firstKey
    val header = msg.header.asInstanceOf[ContentHeader]

    assertEquals(h2.origin, header.origin)
    assertEquals(h2.target, header.target)
    assertEquals(-1, msg.header.seqNum)
    assertEquals(h2.contentType, header.contentType)
    assertEquals(h2.messageId, header.messageId)
    assertEquals(h2.time, header.time)
    assertEquals(new CryptoData(None, None), msg.crypto)
    assertEquals(m2.body, msg.body)
  }

  def testAddContact(): Unit = {
    database.addContact(UserTest.u1)
    val contacts = database.getContacts
    assertEquals(1, contacts.size)
    assertEquals(Option(UserTest.u1), database.getContact(UserTest.u1.address))
  }

  def testAddContactCallback(): Unit = {
    val latch = new CountDownLatch(1)
    val lbm = LocalBroadcastManager.getInstance(context)
    val receiver = new BroadcastReceiver {
      override def onReceive(context: Context, intent: Intent): Unit = latch.countDown()
    }
    lbm.registerReceiver(receiver, new IntentFilter(Database.ActionContactsUpdated))
    database.addContact(UserTest.u1)
    latch.await()
    lbm.unregisterReceiver(receiver)
  }
  
  def testGetContact(): Unit = {
    database.addContact(UserTest.u2)
    assertTrue(database.getContact(UserTest.u1.address).isEmpty)
    val c = database.getContact(UserTest.u2.address)
    assertTrue(c.nonEmpty)
    assertEquals(Option(UserTest.u2), c)
  }

}
