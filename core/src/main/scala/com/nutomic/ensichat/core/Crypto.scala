package com.nutomic.ensichat.core

import java.io._
import java.security._
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{Cipher, CipherOutputStream, KeyGenerator, SecretKey}

import com.nutomic.ensichat.core.Crypto._
import com.nutomic.ensichat.core.body._
import com.nutomic.ensichat.core.header.ContentHeader
import com.nutomic.ensichat.core.interfaces.SettingsInterface
import com.typesafe.scalalogging.Logger

object Crypto {

  /**
   * Algorithm used to generate the local public/private keypair.
   *
   * This keypair is generated on first start, and persistent for the lifetime of the app.
   */
  val PublicKeyAlgorithm = "RSA"

  /**
   * Algorithm used to read public keys.
   */
  val CipherAlgorithm = "RSA/ECB/PKCS1Padding"

  /**
   * Length of the local public/private keypair in bits.
   */
  val PublicKeySize = 4096

  /**
   * Algorithm used for message signing.
   */
  val SigningAlgorithm = "SHA256withRSA"

  /**
   * Algorithm used for the symmetric encryption key.
   *
   * A new key is generated for every message to be encrypted.
   */
  val SymmetricKeyAlgorithm = "AES"

  /**
   * Length of the symmetric encryption key in bits.
   */
  val SymmetricKeyLength = 128

  /**
   * Algorithm used to hash PublicKey and get the address.
   */
  val KeyHashAlgorithm = "SHA-256"

  /**
   * Name of the preference where the local address is stored.
   */
  val LocalAddressKey = "local_address"

  /**
   * Filename of the local private key in [[Crypto.keyFolder]].
   */
  private val PrivateKeyAlias = "local-private"

  /**
   * Filename of the local public key in [[Crypto.keyFolder]].
   */
  val PublicKeyAlias = "local-public"

}

/**
 * Handles all cryptography related operations.
 *
 * @param keyFolder Folder where private and public keys are stored.
 */
class Crypto(settings: SettingsInterface, keyFolder: File) {

  private val logger = Logger(this.getClass)

  /**
   * Generates a new key pair using [[keyFolder]] with [[PublicKeySize]] bits and stores the
   * keys.
   *
   * Does nothing if the key pair already exists.
   */
  private[core] def generateLocalKeys(): Unit = {
    if (localKeysExist)
      return

    var address: Address = null
    var keyPair: KeyPair = null
    do {
      val keyGen = KeyPairGenerator.getInstance(PublicKeyAlgorithm)
      keyGen.initialize(PublicKeySize)
      keyPair = keyGen.genKeyPair()

      address = calculateAddress(keyPair.getPublic)

      // Never generate an invalid address.
    } while(address == Address.Broadcast || address == Address.Null)

    settings.put(LocalAddressKey, address.toString)

    saveKey(PrivateKeyAlias, keyPair.getPrivate)
    saveKey(PublicKeyAlias, keyPair.getPublic)
    logger.info("Generated cryptographic keys, address is " + address)
  }

  /**
   * Returns true if we have a public key stored for the given device.
   */
  private[core] def havePublicKey(address: Address) = new File(keyFolder, address.toString).exists()

  /**
   * Returns the public key for the given device.
   *
   * @throws RuntimeException If the key does not exist.
   */
  @throws[RuntimeException]
  def getPublicKey(address: Address): PublicKey = {
    loadKey(address.toString, classOf[PublicKey])
  }

  /**
   * Adds a new public key for a remote device.
   *
   * @throws RuntimeException If a key already exists for this address.
   */
  @throws[RuntimeException]
  def addPublicKey(address: Address, key: PublicKey): Unit = {
    if (havePublicKey(address))
      throw new RuntimeException("Already have key for " + address + ", not overwriting")

    saveKey(address.toString, key)
  }

  def sign(msg: Message): Message = {
    val sig = Signature.getInstance(SigningAlgorithm)
    val key = loadKey(PrivateKeyAlias, classOf[PrivateKey])
    sig.initSign(key)
    sig.update(msg.body.write)
    new Message(msg.header, new CryptoData(Option(sig.sign), msg.crypto.key), msg.body)
  }

  @throws[InvalidKeyException]
  private[core] def verify(msg: Message, key: Option[PublicKey] = None): Boolean = {
    val sig = Signature.getInstance(SigningAlgorithm)
    lazy val defaultKey = loadKey(msg.header.origin.toString, classOf[PublicKey])
    sig.initVerify(key.getOrElse(defaultKey))
    sig.update(msg.body.write)
    sig.verify(msg.crypto.signature.get)
  }

  /**
   * Returns true if the local private and public key exist.
   */
  private[core] def localKeysExist = new File(keyFolder, PublicKeyAlias).exists()

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

    keyFolder.mkdirs()
    var fos: Option[FileOutputStream] = None
    try {
      fos = Option(new FileOutputStream(path))
      fos.foreach(_.write(key.getEncoded))
    } catch {
      case e: IOException => logger.warn("Failed to save key for alias " + alias, e)
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
  private[core] def loadKey[T](alias: String, keyType: Class[T]): T = {
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
      case e: IOException => logger.error("Failed to load key for alias " + alias, e)
    } finally {
      fis.foreach(_.close())
    }
    val keyFactory = KeyFactory.getInstance(PublicKeyAlgorithm)
    keyType match {
      case c if c == classOf[PublicKey]  =>
        val keySpec = new X509EncodedKeySpec(data)
        keyFactory.generatePublic(keySpec).asInstanceOf[T]
      case c if c == classOf[PrivateKey] =>
        val keySpec = new PKCS8EncodedKeySpec(data)
        keyFactory.generatePrivate(keySpec).asInstanceOf[T]
    }
  }

  private[core] def encryptAndSign(msg: Message, key: Option[PublicKey] = None): Message = {
    sign(encrypt(msg, key))
  }

  private def encrypt(msg: Message, key: Option[PublicKey] = None): Message = {
    // Symmetric encryption of data
    val secretKey = makeSecretKey()
    val symmetricCipher = Cipher.getInstance(SymmetricKeyAlgorithm)
    symmetricCipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val encrypted = new EncryptedBody(copyThroughCipher(symmetricCipher, msg.body.write))

    // Asymmetric encryption of secret key
    val asymmetricCipher = Cipher.getInstance(CipherAlgorithm)
    lazy val defaultKey = loadKey(msg.header.target.toString, classOf[PublicKey])
    asymmetricCipher.init(Cipher.WRAP_MODE, key.getOrElse(defaultKey))

    new Message(msg.header,
      new CryptoData(None, Option(asymmetricCipher.wrap(secretKey))), encrypted)
  }

  @throws[InvalidKeyException]
  def decrypt(msg: Message): Message = {
    // Asymmetric decryption of secret key
    val asymmetricCipher = Cipher.getInstance(CipherAlgorithm)
    asymmetricCipher.init(Cipher.UNWRAP_MODE, loadKey(PrivateKeyAlias, classOf[PrivateKey]))
    val key = asymmetricCipher.unwrap(msg.crypto.key.get, SymmetricKeyAlgorithm, Cipher.SECRET_KEY)

    // Symmetric decryption of data
    val symmetricCipher = Cipher.getInstance(SymmetricKeyAlgorithm)
    symmetricCipher.init(Cipher.DECRYPT_MODE, key)
    val decrypted = copyThroughCipher(symmetricCipher, msg.body.asInstanceOf[EncryptedBody].data)
    val body = msg.header.asInstanceOf[ContentHeader].contentType match {
      case Text.Type              => Text.read(decrypted)
      case UserInfo.Type          => UserInfo.read(decrypted)
      case MessageReceived.Type   => MessageReceived.read(decrypted)
    }
    new Message(msg.header, msg.crypto, body)
  }

  /**
   * Passes data through cipher stream to encrypt or decrypt it and returns int.
   *
   * Operation mode depends on the parameters to [[Cipher#init]].
   *
   * @param cipher An initialized cipher.
   * @param data The data to encrypt or decrypt.
   * @return The encrypted or decrypted data.
   */
  private def copyThroughCipher(cipher: Cipher, data: Array[Byte]): Array[Byte] = {
    val bais = new ByteArrayInputStream(data)
    val baos = new ByteArrayOutputStream()
    val cos = new CipherOutputStream(baos, cipher)
    var i = 0
    val b = new Array[Byte](8192)
    while({i = bais.read(b); i != -1}) {
      cos.write(b, 0, i)
    }
    baos.write(cipher.doFinal())
    baos.toByteArray
  }

  /**
   * Creates a new, random AES key.
   */
  private def makeSecretKey(): SecretKey = {
    val kgen = KeyGenerator.getInstance(SymmetricKeyAlgorithm)
    kgen.init(SymmetricKeyLength)
    val key = kgen.generateKey()
    new SecretKeySpec(key.getEncoded, SymmetricKeyAlgorithm)
  }

  /**
   * Generates the address by hashing the given public key with [[KeyHashAlgorithm]].
   */
  def calculateAddress(key: PublicKey): Address = {
    val md = MessageDigest.getInstance(KeyHashAlgorithm)
    val hash = md.digest(key.getEncoded)
    new Address(hash)
  }

  /**
   * Returns the address of the local node.
   */
  def localAddress = new Address(settings.get(LocalAddressKey, ""))

}
