package com.nutomic.ensichat.aodvv2

import android.content.Context
import android.test.AndroidTestCase
import com.nutomic.ensichat.messages.Crypto
import junit.framework.Assert

object ConnectionInfoTest {

  def generateCi(context: Context) = {
    val crypto = new Crypto(context)
    if (!crypto.localKeysExist)
      crypto.generateLocalKeys()
    new ConnectionInfo(crypto.getLocalPublicKey)
  }

}

class ConnectionInfoTest extends AndroidTestCase {

  def testWriteRead(): Unit = {
    val ci = ConnectionInfoTest.generateCi(getContext)
    val bytes = ci.write
    val body = ConnectionInfo.read(bytes)
    Assert.assertEquals(ci.key, body.asInstanceOf[ConnectionInfo].key)
  }

}