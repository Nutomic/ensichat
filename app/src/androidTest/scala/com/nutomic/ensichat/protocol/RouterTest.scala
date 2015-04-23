package com.nutomic.ensichat.protocol

import java.util.{Date, GregorianCalendar}

import android.test.AndroidTestCase
import com.nutomic.ensichat.protocol.body.{Text, UserInfo}
import com.nutomic.ensichat.protocol.header.ContentHeader
import junit.framework.Assert._

class RouterTest extends AndroidTestCase {

  private def neighbors() = Set[Address](AddressTest.a1, AddressTest.a2, AddressTest.a3)

  private val msg = generateMessage(AddressTest.a1, AddressTest.a4, 1)

  /**
   * Messages should be sent to all neighbors.
   */
  def testFlooding(): Unit = {
    var sentTo = Set[Address]()
    val router: Router = new Router(neighbors,
      (a, m) => {
        sentTo += a
      })

    router.onReceive(msg)
    assertEquals(neighbors(), sentTo)
  }

  def testMessageSame(): Unit = {
    val router: Router = new Router(neighbors,
      (a, m) => {
        assertEquals(msg.header.origin,       m.header.origin)
        assertEquals(msg.header.target,       m.header.target)
        assertEquals(msg.header.seqNum,       m.header.seqNum)
        assertEquals(msg.header.protocolType, m.header.protocolType)
        assertEquals(msg.header.hopCount + 1, m.header.hopCount)
        assertEquals(msg.header.hopLimit,     m.header.hopLimit)
        assertEquals(msg.body, m.body)
        assertEquals(msg.crypto, m.crypto)
      })
    router.onReceive(msg)
  }

  /**
   * Messages from different senders with the same sequence number should be forwarded.
   */
  def testDifferentSenders(): Unit = {
    var sentTo = Set[Address]()
    val router: Router = new Router(neighbors, (a, m) => sentTo += a)

    router.onReceive(msg)
    assertEquals(neighbors(), sentTo)

    sentTo = Set[Address]()
    router.onReceive(generateMessage(AddressTest.a2, AddressTest.a4, 1))
    assertEquals(neighbors(), sentTo)
  }

  /**
   * Messages from the same sender with the same sequence number should be ignored.
   */
  def testIgnores(): Unit = {
    var sentTo = Set[Address]()
    val router: Router = new Router(neighbors, (a, m) => sentTo += a)

    router.onReceive(msg)
    assertEquals(neighbors(), sentTo)

    sentTo = Set[Address]()
    router.onReceive(generateMessage(AddressTest.a1, AddressTest.a2, 1))
    assertTrue(sentTo.isEmpty)
  }
  
  def testDiscardOldIgnores(): Unit = {
    def test(first: Int, second: Int) {
      var sentTo = Set[Address]()
      val router: Router = new Router(neighbors, (a, m) => sentTo += a)
      router.onReceive(generateMessage(AddressTest.a1, AddressTest.a3, first))
      router.onReceive(generateMessage(AddressTest.a1, AddressTest.a3, second))

      sentTo = Set[Address]()
      router.onReceive(generateMessage(AddressTest.a1, AddressTest.a3, first))
      assertEquals(neighbors(), sentTo)
    }

    test(1, ContentHeader.SeqNumRange.last)
    test(ContentHeader.SeqNumRange.last / 2, ContentHeader.SeqNumRange.last)
    test(ContentHeader.SeqNumRange.last / 2, 1)
  }

  def testHopLimit(): Unit = Range(19, 22).foreach { i =>
    val header =
      new ContentHeader(AddressTest.a1, AddressTest.a2, 1, 1, Some(1), Some(new Date()), false, i)
    val msg = new Message(header, new Text(""))
    val router: Router = new Router(neighbors, (a, m) => fail())
    router.onReceive(msg)
  }

  private def generateMessage(sender: Address, receiver: Address, seqNum: Int): Message = {
    val header = new ContentHeader(sender, receiver, seqNum, UserInfo.Type, Some(5),
      Some(new GregorianCalendar(2014, 6, 10).getTime), false)
    new Message(header, new UserInfo("", ""))
  }

}
