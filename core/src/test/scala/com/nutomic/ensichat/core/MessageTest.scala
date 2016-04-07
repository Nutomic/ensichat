package com.nutomic.ensichat.core

import java.io.ByteArrayInputStream

import com.nutomic.ensichat.core.MessageTest._
import com.nutomic.ensichat.core.body.{ConnectionInfo, ConnectionInfoTest, Text}
import com.nutomic.ensichat.core.header.ContentHeaderTest._
import com.nutomic.ensichat.core.header.MessageHeader
import junit.framework.TestCase
import org.junit.Assert._

import scala.collection.immutable.TreeSet

object MessageTest {

  val m1 = new Message(h1, new Text("first"))

  val m2 = new Message(h2, new Text("second"))

  val m3 = new Message(h3, new Text("third"))

  val messages = Set(m1, m2, m3)

}

class MessageTest extends TestCase {

  private lazy val crypto = CryptoTest.getCrypto

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
    val m = new Message(header, ConnectionInfoTest.generateCi())

    val signed = crypto.sign(m)
    val bytes = signed.write
    val read = Message.read(new ByteArrayInputStream(bytes))

    assertEquals(signed, read)
    assertTrue(crypto.verify(read, Option(crypto.getLocalPublicKey)))
  }

  def testSerializeEncrypted(): Unit = {
    MessageTest.messages.foreach{ m =>
      val encrypted = crypto.encryptAndSign(m, Option(crypto.getLocalPublicKey))
      val bytes = encrypted.write

      val read = Message.read(new ByteArrayInputStream(bytes))
      assertEquals(encrypted.crypto, read.crypto)
      assertTrue(crypto.verify(read, Option(crypto.getLocalPublicKey)))
      val decrypted = crypto.decrypt(read)
      assertEquals(m.header, decrypted.header)
      assertEquals(m.body, decrypted.body)
    }
  }

}
