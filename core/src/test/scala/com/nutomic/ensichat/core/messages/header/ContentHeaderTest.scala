package com.nutomic.ensichat.core.messages.header

import java.util.GregorianCalendar

import com.nutomic.ensichat.core.messages
import com.nutomic.ensichat.core.messages.body.Text
import com.nutomic.ensichat.core.messages.header
import com.nutomic.ensichat.core.routing.{Address, AddressTest}
import junit.framework.TestCase
import org.joda.time.DateTime
import org.junit.Assert._

object ContentHeaderTest {

  val h1 = new header.ContentHeader(AddressTest.a1, AddressTest.a2, 1234,
    Text.Type, Some(123), Some(new DateTime(new GregorianCalendar(1970, 1, 1).getTime)), 3)

  val h2 = new header.ContentHeader(AddressTest.a1, AddressTest.a3,
    30000, Text.Type, Some(8765), Some(new DateTime(new GregorianCalendar(2014, 6, 10))), 2)

  val h3 = new header.ContentHeader(AddressTest.a4, AddressTest.a2,
    250, Text.Type, Some(77), Some(new DateTime(new GregorianCalendar(2020, 11, 11).getTime)), 1)

  val h4 = new header.ContentHeader(Address.Null, Address.Broadcast,
    header.ContentHeader.SeqNumRange.last, 0, Some(0xffff), Some(new DateTime(0L)), 6)

  val h5 = new header.ContentHeader(Address.Broadcast, Address.Null,
    0, 0xff, Some(0), Some(new DateTime(0xffffffffL)), 0)

  val headers = Set(h1, h2, h3, h4, h5)

}

class ContentHeaderTest extends TestCase {

  def testSerialize(): Unit = {
    ContentHeaderTest.headers.foreach{h =>
      val bytes = h.write(0)
      assertEquals(bytes.length, h.length)
      val (mh, length) = MessageHeader.read(bytes)
      val chBytes = bytes.drop(mh.length)
      val (header, remaining) = messages.header.ContentHeader.read(mh, chBytes)
      assertEquals(h, header)
      assertEquals(0, remaining.length)
    }
  }

}
