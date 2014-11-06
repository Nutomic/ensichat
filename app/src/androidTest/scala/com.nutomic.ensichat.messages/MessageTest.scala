package com.nutomic.ensichat.messages

import java.io.{PipedInputStream, PipedOutputStream}
import java.util.GregorianCalendar

import android.test.AndroidTestCase
import com.nutomic.ensichat.bluetooth.Device
import com.nutomic.ensichat.messages.MessageTest._
import junit.framework.Assert._

import scala.collection.immutable.TreeSet

object MessageTest {

  val m1 = new TextMessage(new Device.ID("one"), new Device.ID("two"),
    new GregorianCalendar(2014, 10, 29).getTime, "first")

  val m2 = new TextMessage(new Device.ID("one"), new Device.ID("three"),
    new GregorianCalendar(2014, 10, 30).getTime, "second")

  val m3 = new TextMessage(new Device.ID("four"), new Device.ID("two"),
    new GregorianCalendar(2014, 10, 31).getTime, "third")


}

class MessageTest extends AndroidTestCase {

  def testSerialize(): Unit = {
    val pis = new PipedInputStream()
    val pos = new PipedOutputStream(pis)
    val bytes = m1.write(Array[Byte]())
    val (msg, _) = Message.read(bytes)
    assertEquals(m1, msg)
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

}
