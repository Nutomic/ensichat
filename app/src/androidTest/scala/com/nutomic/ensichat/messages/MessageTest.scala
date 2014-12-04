package com.nutomic.ensichat.messages

import java.io.{PipedInputStream, PipedOutputStream}
import java.util.GregorianCalendar

import android.test.AndroidTestCase
import com.nutomic.ensichat.aodvv2.AddressTest
import com.nutomic.ensichat.messages.MessageTest._
import junit.framework.Assert._

import scala.collection.immutable.TreeSet

object MessageTest {

  val m1 = new TextMessage(AddressTest.a1, AddressTest.a2,
    new GregorianCalendar(2014, 10, 29).getTime, "first")

  val m2 = new TextMessage(AddressTest.a1, AddressTest.a3,
    new GregorianCalendar(2014, 10, 30).getTime, "second")

  val m3 = new TextMessage(AddressTest.a4, AddressTest.a2,
    new GregorianCalendar(2014, 10, 31).getTime, "third")

}

class MessageTest extends AndroidTestCase {

  def testSerialize(): Unit = {
    Set(m1, m2, m3).foreach { m =>
      val pis = new PipedInputStream()
      val pos = new PipedOutputStream(pis)
      val bytes = m.write(Array[Byte]())
      val (msg, _) = Message.read(bytes)
      assertEquals(m, msg)
    }
  }

  def testOrder(): Unit = {
    var messages = new TreeSet[Message]()(Message.Ordering)
    messages += MessageTest.m1
    messages += MessageTest.m2
    assertEquals(MessageTest.m1, messages.firstKey)

    messages = new TreeSet[Message]()(Message.Ordering)
    messages += MessageTest.m2
    messages += MessageTest.m3
    assertEquals(MessageTest.m2, messages.firstKey)
  }

}
