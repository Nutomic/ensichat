package com.nutomic.ensichat.protocol

import java.util.GregorianCalendar

import android.test.AndroidTestCase
import com.nutomic.ensichat.protocol.body.UserName
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
        assertEquals(msg, m)
        sentTo += a
      })

    router.onReceive(msg)
    assertEquals(neighbors(), sentTo)
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

  private def generateMessage(sender: Address, receiver: Address, seqNum: Int): Message = {
    val header = new ContentHeader(sender, receiver, seqNum, UserName.Type, 5,
      new GregorianCalendar(2014, 6, 10).getTime)
    new Message(header, new UserName(""))
  }

}
