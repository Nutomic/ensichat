package com.nutomic.ensichat.protocol.header

import android.test.AndroidTestCase
import com.nutomic.ensichat.protocol.header.MessageHeaderTest._
import com.nutomic.ensichat.protocol.{Address, AddressTest}
import junit.framework.Assert._

object MessageHeaderTest {

  val h1 = new MessageHeader(ContentHeader.ContentMessageType, AddressTest.a1, AddressTest.a2, 1234,
    0)

  val h2 = new MessageHeader(ContentHeader.ContentMessageType, Address.Null, Address.Broadcast,
    ContentHeader.SeqNumRange.last, 0xff)

  val h3 = new MessageHeader(ContentHeader.ContentMessageType, Address.Broadcast, Address.Null, 0)

  val headers = Set(h1, h2, h3)

}

class MessageHeaderTest extends AndroidTestCase {

  def testSerialize(): Unit = {
    headers.foreach{h =>
      val bytes = h.write(0)
      val (header, length) = MessageHeader.read(bytes)
      assertEquals(h, header)
      assertEquals(MessageHeader.Length, length)
    }
  }

}
