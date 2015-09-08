package com.nutomic.ensichat.util

import java.util.GregorianCalendar
import java.util.concurrent.CountDownLatch

import android.content._
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.support.v4.content.LocalBroadcastManager
import android.test.AndroidTestCase
import com.nutomic.ensichat.core.body.{CryptoData, Text}
import com.nutomic.ensichat.core.header.ContentHeader
import com.nutomic.ensichat.core.{Address, Message, User}
import com.nutomic.ensichat.util.DatabaseTest._
import junit.framework.Assert._

import scala.collection.SortedSet
import scala.collection.immutable.TreeSet

object DatabaseTest {

  /**
   * Provides a temporary database file that can be deleted easily.
   */
  private class DatabaseContext(context: Context) extends ContextWrapper(context) {
    private val dbFile = "database-test.db"
    override def openOrCreateDatabase(file: String, mode: Int, factory:
    SQLiteDatabase.CursorFactory, errorHandler: DatabaseErrorHandler) =
      context.openOrCreateDatabase(dbFile, mode, factory, errorHandler)
    def deleteDbFile() = context.deleteDatabase(dbFile)
  }

  private val a1 = new Address("A51B74475EE622C3C924DB147668F85E024CA0B44CA146B5E3D3C31A54B34C1E")
  private val a2 = new Address("222229685A73AB8F2F853B3EA515633B7CD5A6ABDC3210BC4EF38F955A14AAF6")
  private val a3 = new Address("3333359893F8810C4024CFC951374AABA1F4DE6347A3D7D8E44918AD1FF2BA36")
  private val a4 = new Address("4444459893F8810C4024CFC951374AABA1F4DE6347A3D7D8E44918AD1FF2BA36")

  private val h1 = new ContentHeader(a1, a2, 1234, Text.Type, Some(123),
    Some(new GregorianCalendar(1970, 1, 1).getTime), 5)
  private val h2 = new ContentHeader(a1, a3, 30000, Text.Type, Some(8765),
    Some(new GregorianCalendar(2014, 6, 10).getTime), 20)
  private val h3 = new ContentHeader(a4, a2, 250, Text.Type, Some(77),
    Some(new GregorianCalendar(2020, 11, 11).getTime), 123)

  private val m1 = new Message(h1, new Text("first"))
  private val m2 = new Message(h2, new Text("second"))
  private val m3 = new Message(h3, new Text("third"))

  private val u1 = new User(a1, "one", "s1")
  private val u2 = new User(a2, "two", "s2")

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
    database.addContact(u1)
    val contacts = database.getContacts
    assertEquals(1, contacts.size)
    assertEquals(Option(u1), database.getContact(u1.address))
  }

  def testAddContactCallback(): Unit = {
    val latch = new CountDownLatch(1)
    val lbm = LocalBroadcastManager.getInstance(context)
    val receiver = new BroadcastReceiver {
      override def onReceive(context: Context, intent: Intent): Unit = latch.countDown()
    }
    lbm.registerReceiver(receiver, new IntentFilter(Database.ActionContactsUpdated))
    database.addContact(u1)
    latch.await()
    lbm.unregisterReceiver(receiver)
  }
  
  def testGetContact(): Unit = {
    database.addContact(u2)
    assertTrue(database.getContact(u1.address).isEmpty)
    val c = database.getContact(u2.address)
    assertTrue(c.nonEmpty)
    assertEquals(Option(u2), c)
  }

}
