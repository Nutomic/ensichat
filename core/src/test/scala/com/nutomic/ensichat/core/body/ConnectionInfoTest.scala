package com.nutomic.ensichat.core.body

import com.nutomic.ensichat.core.CryptoTest
import junit.framework.TestCase
import org.junit.Assert._

object ConnectionInfoTest {

  def generateCi() = {
    val crypto = CryptoTest.getCrypto
    if (!crypto.localKeysExist)
      crypto.generateLocalKeys()
    new ConnectionInfo(crypto.getLocalPublicKey)
  }

}

class ConnectionInfoTest extends TestCase {

  def testWriteRead(): Unit = {
    val ci = ConnectionInfoTest.generateCi()
    val bytes = ci.write
    val body = ConnectionInfo.read(bytes)
    assertEquals(ci.key, body.asInstanceOf[ConnectionInfo].key)
  }

}
