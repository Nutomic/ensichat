package com.nutomic.ensichat.messages

import java.util.GregorianCalendar

import android.test.AndroidTestCase
import com.nutomic.ensichat.bluetooth.Device
import com.nutomic.ensichat.messages.MessageTest._
import junit.framework.Assert._

class CryptoTest extends AndroidTestCase {

  lazy val Crypto: Crypto = new Crypto(getContext.getFilesDir)

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
    val in = new DeviceInfoMessage(new Device.ID("DD:DD:DD:DD:DD:DD"),
      new Device.ID("CC:CC:CC:CC:CC:CC"), new GregorianCalendar(2014, 10, 31).getTime,
      Crypto.getLocalPublicKey)
    val (encrypted, key) = Crypto.encrypt(null, in.write(Array[Byte]()), Crypto.getLocalPublicKey)
    val decrypted = Crypto.decrypt(encrypted, key)
    val out = Message.read(decrypted)._1.asInstanceOf[DeviceInfoMessage]

    assertEquals(in, out)
  }

}
