package com.nutomic.ensichat.core.body

import com.nutomic.ensichat.core.AddressTest
import junit.framework.TestCase
import org.junit.Assert._

class RouteErrorTest extends TestCase {

  def testWriteRead(): Unit = {
    val rerr = new RouteError(AddressTest.a2, 62000)
    val bytes = rerr.write
    val parsed = RouteError.read(bytes)
    assertEquals(rerr, parsed)
  }

}
