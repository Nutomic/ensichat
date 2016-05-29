package com.nutomic.ensichat.core.body

import com.nutomic.ensichat.core.AddressTest
import junit.framework.TestCase
import org.junit.Assert._

class RouteReplyTest extends TestCase {

  def testWriteRead(): Unit = {
    val rrep = new RouteReply(61000, 123)
    val bytes = rrep.write
    val parsed = RouteReply.read(bytes)
    assertEquals(rrep, parsed)
  }

}
