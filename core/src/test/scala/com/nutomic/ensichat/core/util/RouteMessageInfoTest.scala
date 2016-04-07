package com.nutomic.ensichat.core.util

import com.nutomic.ensichat.core.body.{RouteReply, RouteRequest}
import com.nutomic.ensichat.core.header.MessageHeader
import com.nutomic.ensichat.core.{AddressTest, Message}
import junit.framework.TestCase
import org.joda.time.{DateTime, DateTimeUtils, Duration}
import org.junit.Assert._

class RouteMessageInfoTest extends TestCase {

  /**
    * Test case in which we have an entry with the same type, origin and target.
    */
  def testSameMessage(): Unit = {
    val header = new MessageHeader(RouteRequest.Type, AddressTest.a1, AddressTest.a2, 1)
    val msg = new Message(header, new RouteRequest(AddressTest.a3, 2, 3, 1))
    val rmi = new RouteMessageInfo()
    assertFalse(rmi.isMessageRedundant(msg))
    assertTrue(rmi.isMessageRedundant(msg))
  }

  /**
    * Forward a message with a seqnum that is older than the latest.
    */
  def testSeqNumOlder(): Unit = {
    val header1 = new MessageHeader(RouteRequest.Type, AddressTest.a1, AddressTest.a2, 1)
    val msg1 = new Message(header1, new RouteRequest(AddressTest.a3, 0, 0, 0))
    val rmi = new RouteMessageInfo()
    assertFalse(rmi.isMessageRedundant(msg1))

    val header2 = new MessageHeader(RouteRequest.Type, AddressTest.a1, AddressTest.a2, 3)
    val msg2 = new Message(header2, new RouteRequest(AddressTest.a3, 2, 0, 0))
    assertTrue(rmi.isMessageRedundant(msg2))
  }

  /**
    * Announce a route with a metric that is worse than the existing one.
    */
  def testMetricWorse(): Unit = {
    val header1 = new MessageHeader(RouteRequest.Type, AddressTest.a1, AddressTest.a2, 1)
    val msg1 = new Message(header1, new RouteRequest(AddressTest.a3, 1, 0, 2))
    val rmi = new RouteMessageInfo()
    assertFalse(rmi.isMessageRedundant(msg1))

    val header2 = new MessageHeader(RouteRequest.Type, AddressTest.a1, AddressTest.a2, 2)
    val msg2 = new Message(header2, new RouteRequest(AddressTest.a3, 1, 0, 4))
    assertTrue(rmi.isMessageRedundant(msg2))
  }

  /**
    * Announce route with a better metric.
    */
  def testMetricBetter(): Unit = {
    val header1 = new MessageHeader(RouteRequest.Type, AddressTest.a1, AddressTest.a2, 1)
    val msg1 = new Message(header1, new RouteReply(0, 4))
    val rmi = new RouteMessageInfo()
    assertFalse(rmi.isMessageRedundant(msg1))

    val header2 = new MessageHeader(RouteRequest.Type, AddressTest.a1, AddressTest.a2, 2)
    val msg2 = new Message(header2, new RouteReply(0, 2))
    assertFalse(rmi.isMessageRedundant(msg2))
  }

  /**
    * Test that entries are removed after [[RouteMessageInfo.MaxSeqnumLifetime]].
    */
  def testTimeout(): Unit = {
    val rmi = new RouteMessageInfo()
    DateTimeUtils.setCurrentMillisFixed(DateTime.now.getMillis)
    val header = new MessageHeader(RouteRequest.Type, AddressTest.a1, AddressTest.a2, 1)
    val msg = new Message(header, new RouteRequest(AddressTest.a3, 0, 0, 0))
    assertFalse(rmi.isMessageRedundant(msg))

    DateTimeUtils.setCurrentMillisFixed(DateTime.now.plus(Duration.standardSeconds(400)).getMillis)
    assertFalse(rmi.isMessageRedundant(msg))
  }

}