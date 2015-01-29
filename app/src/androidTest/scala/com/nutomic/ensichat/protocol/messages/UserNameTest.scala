package com.nutomic.ensichat.protocol.messages

import android.test.AndroidTestCase
import junit.framework.Assert

class UserNameTest extends AndroidTestCase {

  def testWriteRead(): Unit = {
    val name = new UserName("name")
    val bytes = name.write
    val body = UserName.read(bytes)
    Assert.assertEquals(name, body.asInstanceOf[UserName])
  }

}