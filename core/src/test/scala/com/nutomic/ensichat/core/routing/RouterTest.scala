package com.nutomic.ensichat.core.routing

import java.util.GregorianCalendar
import java.util.concurrent.{CountDownLatch, TimeUnit}

import com.nutomic.ensichat.core.messages.body.{Text, UserInfo}
import com.nutomic.ensichat.core.messages.header.ContentHeader
import com.nutomic.ensichat.core.{messages, routing}
import junit.framework.TestCase
import org.joda.time.DateTime
import org.junit.Assert._

class RouterTest extends TestCase {

  private def neighbors() = Set[routing.Address](AddressTest.a1, AddressTest.a2, AddressTest.a4)

  def testNoRouteFound(): Unit = {
    val msg = generateMessage(AddressTest.a2, AddressTest.a3, 1)
    val latch = new CountDownLatch(1)
    val router = new routing.Router(new LocalRoutesInfo(neighbors),
      (_, _) => fail("Message shouldn't be forwarded"), m => {
        assertEquals(msg, m)
        latch.countDown()
      })
    router.forwardMessage(msg)
    assertTrue(latch.await(1, TimeUnit.SECONDS))
  }

  def testNextHop(): Unit = {
    val msg = generateMessage(AddressTest.a1, AddressTest.a4, 1)
    var sentTo = Set[routing.Address]()
    val router = new routing.Router(new LocalRoutesInfo(neighbors),
      (a, m) => {
        sentTo += a
      }, _ => ())

    router.forwardMessage(msg)
    assertEquals(Set(AddressTest.a4), sentTo)
  }

  def testMessageSame(): Unit = {
    val msg = generateMessage(AddressTest.a1, AddressTest.a4, 1)
    val router = new routing.Router(new LocalRoutesInfo(neighbors),
      (a, m) => {
        assertEquals(msg.header.origin,       m.header.origin)
        assertEquals(msg.header.target,       m.header.target)
        assertEquals(msg.header.seqNum,       m.header.seqNum)
        assertEquals(msg.header.protocolType, m.header.protocolType)
        assertEquals(msg.header.hopCount + 1, m.header.hopCount)
        assertEquals(msg.header.tokens,       m.header.tokens)
        assertEquals(msg.body, m.body)
        assertEquals(msg.crypto, m.crypto)
      }, _ => ())
    router.forwardMessage(msg)
  }

  /**
   * Messages from different senders with the same sequence number should be forwarded.
   */
  def testDifferentSenders(): Unit = {
    var sentTo = Set[routing.Address]()
    val router = new routing.Router(new LocalRoutesInfo(neighbors), (a, m) => sentTo += a, _ => ())

    router.forwardMessage(generateMessage(AddressTest.a1, AddressTest.a4, 1))
    assertEquals(Set(AddressTest.a4), sentTo)

    sentTo = Set[routing.Address]()
    router.forwardMessage(generateMessage(AddressTest.a2, AddressTest.a4, 1))
    assertEquals(Set(AddressTest.a4), sentTo)
  }

  def testSeqNumComparison(): Unit = {
    routing.Router.compare(1, ContentHeader.SeqNumRange.last)
    routing.Router.compare(ContentHeader.SeqNumRange.last / 2, ContentHeader.SeqNumRange.last)
    routing.Router.compare(ContentHeader.SeqNumRange.last / 2, 1)
  }
  
  def testDiscardOldIgnores(): Unit = {
    def test(first: Int, second: Int) {
      var sentTo = Set[routing.Address]()
      val router = new routing.Router(new LocalRoutesInfo(neighbors), (a, m) => sentTo += a, _ => ())
      router.forwardMessage(generateMessage(AddressTest.a1, AddressTest.a4, first))
      router.forwardMessage(generateMessage(AddressTest.a1, AddressTest.a4, second))

      sentTo = Set[routing.Address]()
      router.forwardMessage(generateMessage(AddressTest.a1, AddressTest.a4, first))
      assertEquals(Set(AddressTest.a4), sentTo)
    }

    test(1, ContentHeader.SeqNumRange.last)
    test(ContentHeader.SeqNumRange.last / 2, ContentHeader.SeqNumRange.last)
    test(ContentHeader.SeqNumRange.last / 2, 1)
  }

  def testHopLimit(): Unit = Range(19, 22).foreach { i =>
    val msg = new messages.Message(
      new ContentHeader(AddressTest.a1, AddressTest.a2, 1, 1, Some(1), Some(DateTime.now), 3, i), new Text(""))
    val router = new routing.Router(new LocalRoutesInfo(neighbors), (a, m) => fail(), _ => ())
    router.forwardMessage(msg)
  }

  private def generateMessage(sender: routing.Address, receiver: routing.Address, seqNum: Int): messages.Message = {
    val header = new ContentHeader(sender, receiver, seqNum, UserInfo.Type, Some(5),
      Some(new DateTime(new GregorianCalendar(2014, 6, 10).getTime)), 3)
    new messages.Message(header, new UserInfo("", ""))
  }

}
