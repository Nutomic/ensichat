package com.nutomic.ensichat.core.body

import junit.framework.TestCase
import org.junit.Assert._

class UserInfoTest extends TestCase {

  def testWriteRead(): Unit = {
    val name = new UserInfo("name", "status")
    val bytes = name.write
    val body = UserInfo.read(bytes)
    assertEquals(name, body.asInstanceOf[UserInfo])
  }

}
