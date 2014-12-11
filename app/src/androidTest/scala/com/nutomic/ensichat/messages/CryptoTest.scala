package com.nutomic.ensichat.messages

import android.test.AndroidTestCase
import com.nutomic.ensichat.aodvv2.MessageTest._
import junit.framework.Assert._

class CryptoTest extends AndroidTestCase {

  lazy val Crypto: Crypto = new Crypto(getContext)

  override def setUp(): Unit = {
    super.setUp()
    if (!Crypto.localKeysExist) {
      Crypto.generateLocalKeys()
    }
  }

  def testSignVerify(): Unit = {
    messages.foreach { m =>
      val signed = Crypto.sign(m)
      assertTrue(Crypto.verify(signed, Crypto.getLocalPublicKey))
      assertEquals(m.Header, signed.Header)
      assertEquals(m.Body, signed.Body)
    }
  }

  def testEncryptDecrypt(): Unit = {
    messages.foreach{ m =>
      val encrypted = Crypto.encrypt(Crypto.sign(m), Crypto.getLocalPublicKey)
      val decrypted = Crypto.decrypt(encrypted)
      assertEquals(m.Body, decrypted.Body)
      assertEquals(m.Header, encrypted.Header)
    }
  }

}
