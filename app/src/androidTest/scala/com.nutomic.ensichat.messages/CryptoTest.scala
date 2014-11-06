package com.nutomic.ensichat.messages

import android.test.AndroidTestCase
import junit.framework.Assert._
import com.nutomic.ensichat.messages.MessageTest._

class CryptoTest extends AndroidTestCase {

  var encrypt: Crypto = _

  override def setUp(): Unit = {
    super.setUp()
    encrypt = new Crypto(getContext.getFilesDir)
    if (!encrypt.localKeysExist) {
      encrypt.generateLocalKeys()
    }
  }

  def testSignVerify(): Unit = {
    val sig = encrypt.calculateSignature(m1)
    assertTrue(encrypt.isValidSignature(m1, sig, encrypt.getLocalPublicKey))
  }

}
