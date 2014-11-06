package com.nutomic.ensichat.messages

import java.io.{File, FileInputStream, FileOutputStream, IOException}
import java.security._
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}

import android.content.Context
import android.util.Log
import com.nutomic.ensichat.bluetooth.Device
import com.nutomic.ensichat.messages.Crypto._

object Crypto {

  /**
   * Name of algorithm used for key generation.
   */
  val KeyAlgorithm = "RSA"

  /**
   * Number of bits for local key pair.
   */
  val KeySize = 2048

  /**
   * Name of algorithm used for message signing.
   */
  val SignAlgorithm = "SHA256withRSA"

}

/**
 * Handles all cryptography related operations.
 *
 * @param filesDir The return value of [[android.content.Context#getFilesDir]].
 * @note We can't use [[KeyStore]], because it requires certificates, and does not work for
 *       private keys
 */
class Crypto(filesDir: File) {

  private val Tag = "Crypto"

  private val PrivateKeyAlias = "local-private"

  private val PublicKeyAlias = "local-public"

  /**
   * Generates a new key pair using [[KeyAlgorithm]] with [[KeySize]] bits and stores the keys.
   */
  def generateLocalKeys(): Unit = {
    Log.i(Tag, "Generating cryptographic keys with algorithm: " + KeyAlgorithm)
    val keyGen = KeyPairGenerator.getInstance(KeyAlgorithm)
    keyGen.initialize(KeySize)
    val keyPair = keyGen.genKeyPair()

    saveKey(PrivateKeyAlias, keyPair.getPrivate)
    saveKey(PublicKeyAlias, keyPair.getPublic)
  }

  /**
   * Returns true if we have a public key stored for the given device.
   */
  def havePublicKey(device: Device.ID): Boolean = new File(keyFolder, device.toString).exists()

  /**
   * Adds a new public key for a remote device.
   *
   * If a key for the device already exists, nothing is done.
   *
   * @param device The device to wchi the key belongs.
   * @param key The new key to add.
   */
  def addPublicKey(device: Device.ID, key: PublicKey): Unit = {
    if (!havePublicKey(device)) {
      saveKey(device.toString, key)
    } else {
      Log.i(Tag, "Already have key for " + device.toString + ", not overwriting")
    }
  }

  /**
   * Checks if the message was properly signed.
   *
   * @param message The message to verify.
   * @param signature The signature that was sent
   * @return True if the signature is valid.
   */
  def isValidSignature(message: Message, signature: Array[Byte], key: PublicKey = null): Boolean = {
    val publicKey =
      if (key != null) key
      else loadKey(message.sender.toString, classOf[PublicKey])
    val sig = Signature.getInstance(SignAlgorithm)
    sig.initVerify(key)
    sig.update(message.getBytes)
    sig.verify(signature)
  }

  /**
   * Returns a cryptographic signature for the given message (using local private key).
   */
  def calculateSignature(message: Message): Array[Byte] = {
    val sig = Signature.getInstance(SignAlgorithm)
    val key = loadKey(PrivateKeyAlias, classOf[PrivateKey])
    sig.initSign(key)
    sig.update(message.getBytes)
    sig.sign
  }

  /**
   * Returns true if the local private and public key exist.
   */
  def localKeysExist = new File(keyFolder, PublicKeyAlias).exists()

  /**
   * Returns the local public key.
   */
  def getLocalPublicKey = loadKey(PublicKeyAlias, classOf[PublicKey])

  /**
   * Permanently stores the given key.
   *
   * The key can later be retrieved with [[loadKey]] and the same alias.
   *
   * @param alias Unique name under which the key should be stored.
   * @param key The (private or public) key to store.
   * @throws RuntimeException If a key with the given alias already exists.
   */
  private def saveKey(alias: String, key: Key): Unit = {
    val path = new File(keyFolder, alias)
    if (path.exists()) {
      throw new RuntimeException("Requested to overwrite existing key with alias " + alias +
        ", aborting")
    }

    keyFolder.mkdir()
    var fos: Option[FileOutputStream] = None
    try {
      fos = Option(new FileOutputStream(path))
      fos.foreach(_.write(key.getEncoded))
    } catch {
      case e: IOException => Log.w(Tag, "Failed to save key for alias " + alias, e)
    } finally {
      fos.foreach(_.close())
    }
  }

  /**
   * Loads a key that was stored with [[saveKey]].
   *
   * @param alias The alias under which the key was stored.
   * @param keyType The type of key, either [[PrivateKey]] or [[PublicKey]].
   * @tparam T Deduced from keyType.
   * @return The key read from storage.
   * @throws RuntimeException If the key does not exist.
   */
  private def loadKey[T](alias: String, keyType: Class[T]): T = {
    val path = new File(keyFolder, alias)
    if (!path.exists()) {
      throw new RuntimeException("The requested key with alias " + alias + " does not exist")
    }

    var fis: Option[FileInputStream] = None
    var data: Array[Byte] = null
    try {
      fis = Option(new FileInputStream(path))
      data = new Array[Byte](path.length().asInstanceOf[Int])
      fis.foreach(_.read(data))
    } catch {
      case e: IOException => Log.e(Tag, "Failed to load key for alias " + alias, e)
    } finally {
      fis.foreach(_.close())
    }
    val keyFactory = KeyFactory.getInstance(KeyAlgorithm)
    keyType match {
      case c if c == classOf[PublicKey]  =>
        val keySpec = new X509EncodedKeySpec(data)
        keyFactory.generatePublic(keySpec).asInstanceOf[T]
      case c if c == classOf[PrivateKey] =>
        val keySpec = new PKCS8EncodedKeySpec(data)
        keyFactory.generatePrivate(keySpec).asInstanceOf[T]
    }
  }

  /**
   * Returns the folder where keys are stored.
   */
  private def keyFolder = new File(filesDir, "keys")

}
