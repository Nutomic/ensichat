package com.nutomic.ensichat.protocol

import java.io.ByteArrayInputStream

import android.test.AndroidTestCase
import com.nutomic.ensichat.protocol.MessageTest._
import com.nutomic.ensichat.protocol.body.{ConnectionInfo, ConnectionInfoTest, Text}
import com.nutomic.ensichat.protocol.header.ContentHeaderTest._
import com.nutomic.ensichat.protocol.header.MessageHeader
import junit.framework.Assert._

import scala.collection.immutable.TreeSet

object MessageTest {

  val m1 = new Message(h1, new Text("first"))

  val m2 = new Message(h2, new Text("second"))

  val m3 = new Message(h3, new Text("third"))

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
    val header = new MessageHeader(ConnectionInfo.Type, AddressTest.a4, AddressTest.a2, 0)
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
      assertEquals(encrypted.crypto, read.crypto)
      val decrypted = crypto.decrypt(read)
      assertEquals(m.header, decrypted.header)
      assertEquals(m.body, decrypted.body)
      assertTrue(crypto.verify(decrypted, crypto.getLocalPublicKey))
    }
  }

}
