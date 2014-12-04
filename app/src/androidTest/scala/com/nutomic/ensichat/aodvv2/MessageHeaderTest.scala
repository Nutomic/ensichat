package com.nutomic.ensichat.aodvv2

import java.util.Date

import android.test.AndroidTestCase
import junit.framework.Assert

object MessageHeaderTest {

  val h1 = new MessageHeader(Data.Type, MessageHeader.DefaultHopLimit, new Date(), AddressTest.a3,
    AddressTest.a4, 456, 123)

  val h2 = new MessageHeader(0xfff, 0, new Date(0xffffffff), Address.Null, Address.Broadcast, 0,
    0xff)

  val h3 = new MessageHeader(0xfff, 0xff, new Date(0), Address.Broadcast, Address.Null, 0xffff, 0)

}

class MessageHeaderTest extends AndroidTestCase {

  def testSerialize(): Unit = {
    val ci = ConnectionInfoTest.generateCi(getContext)
    val bytes = MessageHeaderTest.h1.write(ci)
    val header = MessageHeader.read(bytes)
    Assert.assertEquals(MessageHeaderTest.h1, header)
    Assert.assertEquals(bytes.length, header.Length)
  }

}