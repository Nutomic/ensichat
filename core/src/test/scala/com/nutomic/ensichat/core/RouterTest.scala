package com.nutomic.ensichat.core

import java.util.{Date, GregorianCalendar}

import com.nutomic.ensichat.core.body.{Text, UserInfo}
import com.nutomic.ensichat.core.header.ContentHeader
import junit.framework.TestCase
import org.junit.Assert._

class RouterTest extends TestCase {

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

    router.forwardMessage(msg)
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
    router.forwardMessage(msg)
  }

  /**
   * Messages from different senders with the same sequence number should be forwarded.
   */
  def testDifferentSenders(): Unit = {
    var sentTo = Set[Address]()
    val router: Router = new Router(neighbors, (a, m) => sentTo += a)

    router.forwardMessage(msg)
    assertEquals(neighbors(), sentTo)

    sentTo = Set[Address]()
    router.forwardMessage(generateMessage(AddressTest.a2, AddressTest.a4, 1))
    assertEquals(neighbors(), sentTo)
  }
  
  def testDiscardOldIgnores(): Unit = {
    def test(first: Int, second: Int) {
      var sentTo = Set[Address]()
      val router: Router = new Router(neighbors, (a, m) => sentTo += a)
      router.forwardMessage(generateMessage(AddressTest.a1, AddressTest.a3, first))
      router.forwardMessage(generateMessage(AddressTest.a1, AddressTest.a3, second))

      sentTo = Set[Address]()
      router.forwardMessage(generateMessage(AddressTest.a1, AddressTest.a3, first))
      assertEquals(neighbors(), sentTo)
    }

    test(1, ContentHeader.SeqNumRange.last)
    test(ContentHeader.SeqNumRange.last / 2, ContentHeader.SeqNumRange.last)
    test(ContentHeader.SeqNumRange.last / 2, 1)
  }

  def testHopLimit(): Unit = Range(19, 22).foreach { i =>
    val msg = new Message(
      new ContentHeader(AddressTest.a1, AddressTest.a2, 1, 1, Some(1), Some(new Date()), i), new Text(""))
    val router: Router = new Router(neighbors, (a, m) => fail())
    router.forwardMessage(msg)
  }

  private def generateMessage(sender: Address, receiver: Address, seqNum: Int): Message = {
    val header = new ContentHeader(sender, receiver, seqNum, UserInfo.Type, Some(5),
      Some(new GregorianCalendar(2014, 6, 10).getTime))
    new Message(header, new UserInfo("", ""))
  }

}
