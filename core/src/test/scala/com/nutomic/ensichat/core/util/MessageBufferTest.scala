package com.nutomic.ensichat.core.util

import java.util.concurrent.{TimeUnit, CountDownLatch}

import com.nutomic.ensichat.core.MessageTest
import junit.framework.TestCase
import org.junit.Assert._

class MessageBufferTest extends TestCase {

  def testGetMessages(): Unit = {
    val buffer = new MessageBuffer(() => _)
    buffer.addMessage(MessageTest.m1)
    buffer.addMessage(MessageTest.m2)
    val msgs = buffer.getMessages(MessageTest.m1.header.target)
    assertEquals(1, msgs.size)
    assertEquals(MessageTest.m1, msgs.head)
  }

  def testRetryMessage(): Unit = {
    val latch = new CountDownLatch(1)
    val buffer = new MessageBuffer({e =>
      assertEquals(MessageTest.m1.header.target, e)
      latch.countDown()
    })
    buffer.addMessage(MessageTest.m1)
    assertTrue(latch.await(15, TimeUnit.SECONDS))
  }

}
