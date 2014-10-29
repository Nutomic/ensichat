package com.nutomic.ensichat.messages

import java.io.{PipedInputStream, PipedOutputStream}
import java.util.GregorianCalendar

import android.test.AndroidTestCase
import com.nutomic.ensichat.bluetooth.Device
import junit.framework.Assert._

import scala.collection.immutable.TreeSet

object TextMessageTest {

  val m1 = new TextMessage(new Device.ID("one"), new Device.ID("two"), "first",
    new GregorianCalendar(2014, 10, 29).getTime)

  val m2 = new TextMessage(new Device.ID("one"), new Device.ID("three"), "second",
    new GregorianCalendar(2014, 10, 30).getTime)

  val m3 = new TextMessage(new Device.ID("four"), new Device.ID("two"), "third",
    new GregorianCalendar(2014, 10, 31).getTime)

}

class TextMessageTest extends AndroidTestCase {

  def testSerialize(): Unit = {
    val pis = new PipedInputStream()
    val pos = new PipedOutputStream(pis)

    TextMessageTest.m1.write(pos)

    val unpacked = TextMessage.fromStream(pis)

    assertEquals(TextMessageTest.m1, unpacked)
  }

  def testOrder(): Unit = {
    var messages = new TreeSet[TextMessage]()(TextMessage.Ordering)
    messages += TextMessageTest.m1
    messages += TextMessageTest.m2
    assertEquals(TextMessageTest.m1, messages.firstKey)

    messages = new TreeSet[TextMessage]()(TextMessage.Ordering)
    messages += TextMessageTest.m2
    messages += TextMessageTest.m3
    assertEquals(TextMessageTest.m2, messages.firstKey)
  }


}
