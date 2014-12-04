package com.nutomic.ensichat.messages

import android.test.AndroidTestCase
import com.nutomic.ensichat.messages.MessageTest._
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
    val sig = Crypto.calculateSignature(m1)
    assertTrue(Crypto.isValidSignature(m1, sig, Crypto.getLocalPublicKey))
  }

  def testEncryptDecrypt(): Unit = {
    val (encrypted, key) =
      Crypto.encrypt(null, MessageTest.m1.write(Array[Byte]()), Crypto.getLocalPublicKey)
    val decrypted = Crypto.decrypt(encrypted, key)
    assertEquals(MessageTest.m1, Message.read(decrypted)._1)
  }

}
