package com.nutomic.ensichat.aodvv2

import java.util.GregorianCalendar

import android.test.AndroidTestCase
import com.nutomic.ensichat.aodvv2.MessageHeaderTest._
import junit.framework.Assert._

object MessageHeaderTest {

  val h1 = new MessageHeader(Text.Type, MessageHeader.DefaultHopLimit, AddressTest.a1,
    AddressTest.a2, 1234, 0, new GregorianCalendar(1970, 1, 1).getTime, 567, 8)

  val h2 = new MessageHeader(Text.Type, 0, AddressTest.a1, AddressTest.a3, 8765, 234,
   new GregorianCalendar(2014, 6, 10).getTime, 0, 0xff)

  val h3 = new MessageHeader(Text.Type, 0xff, AddressTest.a4, AddressTest.a2, 0, 56,
    new GregorianCalendar(2020, 11, 11).getTime, 0xffff, 0)

  val h4 = new MessageHeader(0xfff, 0, Address.Null, Address.Broadcast, 0, 0xff,
    new GregorianCalendar(1990, 1, 1).getTime, 0, 0xff)

  val h5 = new MessageHeader(ConnectionInfo.Type, 0xff, Address.Broadcast, Address.Null, 0xffff, 0,
    new GregorianCalendar(2035, 12, 31).getTime, 0xffff, 0)

  val headers = Set(h1, h2, h3, h4, h5)

}

class MessageHeaderTest extends AndroidTestCase {

  def testSerialize(): Unit = {
    headers.foreach{h =>
      val bytes = h.write(0)
      val header = MessageHeader.read(bytes)
      assertEquals(h, header)
      assertEquals(bytes.length, header.Length)
    }
  }

}