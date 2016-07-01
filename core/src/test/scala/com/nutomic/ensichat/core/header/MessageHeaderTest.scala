package com.nutomic.ensichat.core.header

import com.nutomic.ensichat.core.header.MessageHeaderTest._
import com.nutomic.ensichat.core.{Address, AddressTest}
import junit.framework.TestCase
import org.junit.Assert._

object MessageHeaderTest {

  val h1 = new MessageHeader(ContentHeader.ContentMessageType, AddressTest.a1, AddressTest.a2, 3,
    0)

  val h2 = new MessageHeader(ContentHeader.ContentMessageType, Address.Null, Address.Broadcast,
    ContentHeader.SeqNumRange.last, 6, 3)

  val h3 = new MessageHeader(ContentHeader.ContentMessageType, Address.Broadcast, Address.Null, 0, 3)

  val headers = Set(h1, h2, h3)

}

class MessageHeaderTest extends TestCase {

  def testSerialize(): Unit = {
    headers.foreach{h =>
      val bytes = h.write(0)
      val (header, length) = MessageHeader.read(bytes)
      assertEquals(h, header)
      assertEquals(MessageHeader.Length, length)
    }
  }

}
