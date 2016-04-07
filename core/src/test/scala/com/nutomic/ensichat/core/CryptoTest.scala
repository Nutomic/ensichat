package com.nutomic.ensichat.core

import java.io.File

import com.nutomic.ensichat.core.interfaces.SettingsInterface
import junit.framework.TestCase
import org.junit.Assert._

object CryptoTest {

  class TestSettings extends SettingsInterface {
    private var map = Map[String, Any]()
    override def get[T](key: String, default: T): T = map.getOrElse(key, default).asInstanceOf[T]
    override def put[T](key: String, value: T): Unit = map += (key -> value)
  }

  def getCrypto: Crypto = {
    val tempFolder = new File(System.getProperty("testDir"), "/crypto/")
    val crypto = new Crypto(new TestSettings(), tempFolder)
    if (!crypto.localKeysExist) {
      crypto.generateLocalKeys()
    }
    crypto
  }

}

class CryptoTest extends TestCase {

  private lazy val crypto = CryptoTest.getCrypto

  def testSignVerify(): Unit = {
    MessageTest.messages.foreach { m =>
      val signed = crypto.sign(m)
      assertTrue(crypto.verify(signed, Option(crypto.getLocalPublicKey)))
      assertEquals(m.header, signed.header)
      assertEquals(m.body, signed.body)
    }
  }

  def testEncryptDecrypt(): Unit = {
    MessageTest.messages.foreach{ m =>
      val encrypted = crypto.encryptAndSign(m, Option(crypto.getLocalPublicKey))
      assertTrue(crypto.verify(encrypted, Option(crypto.getLocalPublicKey)))
      val decrypted = crypto.decrypt(encrypted)
      assertEquals(m.body, decrypted.body)
      assertEquals(m.header, encrypted.header)
    }
  }

}
