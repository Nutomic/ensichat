package com.nutomic.ensichat.protocol

import android.test.AndroidTestCase
import com.nutomic.ensichat.protocol.messages.MessageTest
import junit.framework.Assert._

class CryptoTest extends AndroidTestCase {

  private lazy val crypto: Crypto = new Crypto(getContext)

  override def setUp(): Unit = {
    super.setUp()
    if (!crypto.localKeysExist) {
      crypto.generateLocalKeys()
    }
  }

  def testSignVerify(): Unit = {
    MessageTest.messages.foreach { m =>
      val signed = crypto.sign(m)
      assertTrue(crypto.verify(signed, crypto.getLocalPublicKey))
      assertEquals(m.Header, signed.Header)
      assertEquals(m.Body, signed.Body)
    }
  }

  def testEncryptDecrypt(): Unit = {
    MessageTest.messages.foreach{ m =>
      val encrypted = crypto.encrypt(crypto.sign(m), crypto.getLocalPublicKey)
      val decrypted = crypto.decrypt(encrypted)
      assertEquals(m.Body, decrypted.Body)
      assertEquals(m.Header, encrypted.Header)
    }
  }

}
