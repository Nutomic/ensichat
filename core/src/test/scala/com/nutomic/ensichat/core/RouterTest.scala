package com.nutomic.ensichat.core

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.{Date, GregorianCalendar}

import com.nutomic.ensichat.core.body.{Text, UserInfo}
import com.nutomic.ensichat.core.header.ContentHeader
import com.nutomic.ensichat.core.util.LocalRoutesInfo
import junit.framework.TestCase
import org.junit.Assert._

class RouterTest extends TestCase {

  private def neighbors() = Set[Address](AddressTest.a1, AddressTest.a2, AddressTest.a4)

  def testNoRouteFound(): Unit = {
    val msg = generateMessage(AddressTest.a2, AddressTest.a3, 1)
    val latch = new CountDownLatch(1)
    val router = new Router(new LocalRoutesInfo(neighbors),
      (_, _) => fail("Message shouldn't be forwarded"), m => {
        assertEquals(msg, m)
        latch.countDown()
      })
    router.forwardMessage(msg)
    assertTrue(latch.await(1, TimeUnit.SECONDS))
  }

  def testNextHop(): Unit = {
    val msg = generateMessage(AddressTest.a1, AddressTest.a4, 1)
    var sentTo = Set[Address]()
    val router = new Router(new LocalRoutesInfo(neighbors),
      (a, m) => {
        sentTo += a
      }, _ => ())

    router.forwardMessage(msg)
    assertEquals(Set(AddressTest.a4), sentTo)
  }

  def testMessageSame(): Unit = {
    val msg = generateMessage(AddressTest.a1, AddressTest.a4, 1)
    val router = new Router(new LocalRoutesInfo(neighbors),
      (a, m) => {
        assertEquals(msg.header.origin,       m.header.origin)
        assertEquals(msg.header.target,       m.header.target)
        assertEquals(msg.header.seqNum,       m.header.seqNum)
        assertEquals(msg.header.protocolType, m.header.protocolType)
        assertEquals(msg.header.hopCount + 1, m.header.hopCount)
        assertEquals(msg.header.hopLimit,     m.header.hopLimit)
        assertEquals(msg.body, m.body)
        assertEquals(msg.crypto, m.crypto)
      }, _ => ())
    router.forwardMessage(msg)
  }

  /**
   * Messages from different senders with the same sequence number should be forwarded.
   */
  def testDifferentSenders(): Unit = {
    var sentTo = Set[Address]()
    val router = new Router(new LocalRoutesInfo(neighbors), (a, m) => sentTo += a, _ => ())

    router.forwardMessage(generateMessage(AddressTest.a1, AddressTest.a4, 1))
    assertEquals(Set(AddressTest.a4), sentTo)

    sentTo = Set[Address]()
    router.forwardMessage(generateMessage(AddressTest.a2, AddressTest.a4, 1))
    assertEquals(Set(AddressTest.a4), sentTo)
  }

  def testSeqNumComparison(): Unit = {
    Router.compare(1, ContentHeader.SeqNumRange.last)
    Router.compare(ContentHeader.SeqNumRange.last / 2, ContentHeader.SeqNumRange.last)
    Router.compare(ContentHeader.SeqNumRange.last / 2, 1)
  }
  
  def testDiscardOldIgnores(): Unit = {
    def test(first: Int, second: Int) {
      var sentTo = Set[Address]()
      val router = new Router(new LocalRoutesInfo(neighbors), (a, m) => sentTo += a, _ => ())
      router.forwardMessage(generateMessage(AddressTest.a1, AddressTest.a4, first))
      router.forwardMessage(generateMessage(AddressTest.a1, AddressTest.a4, second))

      sentTo = Set[Address]()
      router.forwardMessage(generateMessage(AddressTest.a1, AddressTest.a4, first))
      assertEquals(Set(AddressTest.a4), sentTo)
    }

    test(1, ContentHeader.SeqNumRange.last)
    test(ContentHeader.SeqNumRange.last / 2, ContentHeader.SeqNumRange.last)
    test(ContentHeader.SeqNumRange.last / 2, 1)
  }

  def testHopLimit(): Unit = Range(19, 22).foreach { i =>
    val msg = new Message(
      new ContentHeader(AddressTest.a1, AddressTest.a2, 1, 1, Some(1), Some(new Date()), i), new Text(""))
    val router = new Router(new LocalRoutesInfo(neighbors), (a, m) => fail(), _ => ())
    router.forwardMessage(msg)
  }

  private def generateMessage(sender: Address, receiver: Address, seqNum: Int): Message = {
    val header = new ContentHeader(sender, receiver, seqNum, UserInfo.Type, Some(5),
      Some(new GregorianCalendar(2014, 6, 10).getTime))
    new Message(header, new UserInfo("", ""))
  }

}
