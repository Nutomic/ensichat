package com.nutomic.ensichat.protocol.messages

import java.io.ByteArrayInputStream
import java.util.GregorianCalendar

import android.test.AndroidTestCase
import com.nutomic.ensichat.protocol.messages.MessageHeaderTest._
import com.nutomic.ensichat.protocol.messages.MessageTest._
import com.nutomic.ensichat.protocol.{AddressTest, Crypto}
import junit.framework.Assert._

import scala.collection.immutable.TreeSet

object MessageTest {

  val m1 = new Message(h1, new Text("first", new GregorianCalendar(1970, 1, 1).getTime))

  val m2 = new Message(h2, new Text("second", new GregorianCalendar(2014, 6, 10).getTime))

  val m3 = new Message(h3, new Text("third", new GregorianCalendar(2020, 11, 11).getTime))

  val messages = Set(m1, m2, m3)

}

class MessageTest extends AndroidTestCase {

  private lazy val crypto: Crypto = new Crypto(getContext)

  override def setUp(): Unit = {
    super.setUp()
    if (!crypto.localKeysExist) {
      crypto.generateLocalKeys()
    }
  }

  def testOrder(): Unit = {
    var messages = new TreeSet[Message]()(Message.Ordering)
    messages += m1
    messages += m2
    assertEquals(m1, messages.firstKey)

    messages = new TreeSet[Message]()(Message.Ordering)
    messages += m2
    messages += m3
    assertEquals(m2, messages.firstKey)
  }

  def testSerializeSigned(): Unit = {
    val header = new MessageHeader(ConnectionInfo.Type, 0xff, AddressTest.a4, AddressTest.a2, 0, 56)
    val m = new Message(header, ConnectionInfoTest.generateCi(getContext))

    val signed = crypto.sign(m)
    val bytes = signed.write
    val read = Message.read(new ByteArrayInputStream(bytes))

    assertEquals(signed, read)
    assertTrue(crypto.verify(read, crypto.getLocalPublicKey))
  }

  def testSerializeEncrypted(): Unit = {
    MessageTest.messages.foreach{ m =>
      val signed = crypto.sign(m)
      val encrypted = crypto.encrypt(signed, crypto.getLocalPublicKey)
      val bytes = encrypted.write

      val read = Message.read(new ByteArrayInputStream(bytes))
      assertEquals(encrypted.Crypto, read.Crypto)
      val decrypted = crypto.decrypt(read)
      assertEquals(m.Header, decrypted.Header)
      assertEquals(m.Body, decrypted.Body)
      assertTrue(crypto.verify(decrypted, crypto.getLocalPublicKey))
    }
  }

}
