package com.nutomic.ensichat.core.util

import java.io.File
import java.util.GregorianCalendar
import java.util.concurrent.CountDownLatch

import com.nutomic.ensichat.core.body.Text
import com.nutomic.ensichat.core.header.ContentHeader
import com.nutomic.ensichat.core.interfaces.{SettingsInterface, CallbackInterface}
import com.nutomic.ensichat.core.util.DatabaseTest._
import com.nutomic.ensichat.core.{Address, Message, User}
import junit.framework.Assert._
import junit.framework.TestCase

object DatabaseTest {

  private val a1 = new Address("A51B74475EE622C3C924DB147668F85E024CA0B44CA146B5E3D3C31A54B34C1E")
  private val a2 = new Address("222229685A73AB8F2F853B3EA515633B7CD5A6ABDC3210BC4EF38F955A14AAF6")
  private val a3 = new Address("3333359893F8810C4024CFC951374AABA1F4DE6347A3D7D8E44918AD1FF2BA36")
  private val a4 = new Address("4444459893F8810C4024CFC951374AABA1F4DE6347A3D7D8E44918AD1FF2BA36")

  private val h1 = new ContentHeader(a2, a1, -1, Text.Type, Some(123),
    Some(new GregorianCalendar(1970, 1, 1).getTime), 0)
  private val h2 = new ContentHeader(a1, a3, -1, Text.Type, Some(8765),
    Some(new GregorianCalendar(2014, 6, 10).getTime), 0)
  private val h3 = new ContentHeader(a4, a2, -1, Text.Type, Some(77),
    Some(new GregorianCalendar(2020, 11, 11).getTime), 0)

  private val m1 = new Message(h1, new Text("first"))
  private val m2 = new Message(h2, new Text("second"))
  private val m3 = new Message(h3, new Text("third"))

  private val u1 = new User(a1, "one", "s1")
  private val u2 = new User(a2, "two", "s2")
  private val u3 = new User(a2, "two-updated", "s2-updated")

}

class DatabaseTest extends TestCase {

  private val databaseFile = File.createTempFile("ensichat-test", ".db")

  private val latch = new CountDownLatch(1)

  private val database = new Database(databaseFile, new SettingsInterface {
      override def get[T](key: String, default: T): T = default
      override def put[T](key: String, value: T): Unit = {}
    }, new CallbackInterface {
      override def onConnectionsChanged(): Unit = {}
      override def onContactsUpdated(): Unit = {
        latch.countDown()
      }
      override def onMessageReceived(msg: Message): Unit = {}
    })

  override def tearDown(): Unit = {
    super.tearDown()
    database.close()
    databaseFile.delete()
  }

  def testMessageSelect(): Unit = {
    database.onMessageReceived(m1)
    database.onMessageReceived(m2)
    database.onMessageReceived(m3)
    val msg = database.getMessages(a2)
    assertEquals(Seq(m1, m3), msg)
  }

  def testAddContact(): Unit = {
    assertEquals(0, database.getContacts.size)
    database.addContact(u1)
    val contacts = database.getContacts
    assertEquals(1, contacts.size)
    assertEquals(Option(u1), database.getContact(u1.address))
  }

  def testAddContactCallback(): Unit = {
    database.addContact(u1)
    latch.await()
  }

  def testGetContact(): Unit = {
    assertFalse(database.getContact(u2.address).isDefined)
    database.addContact(u2)
    val c = database.getContact(u2.address)
    assertEquals(u2, c.get)
  }

  def testUpdateContact(): Unit = {
    database.addContact(u2)
    database.updateContact(u3)
    val c = database.getContact(u2.address)
    assertEquals(u3, c.get)
  }

  def testUpdateNonExistingContact(): Unit = {
    try {
      database.updateContact(u3)
      fail()
    } catch {
      case _: AssertionError =>
    }
  }

}
