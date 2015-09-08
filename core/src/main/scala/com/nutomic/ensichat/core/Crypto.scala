package com.nutomic.ensichat.core

import java.io._
import java.security._
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{Cipher, CipherOutputStream, KeyGenerator, SecretKey}

import com.nutomic.ensichat.core.Crypto._
import com.nutomic.ensichat.core.body._
import com.nutomic.ensichat.core.header.ContentHeader
import com.nutomic.ensichat.core.interfaces.{Log, Settings}

object Crypto {

  /**
   * Name of algorithm used for key generation.
   */
  val KeyAlgorithm = "RSA"

  /**
   * Number of bits for local key pair.
   */
  val KeySize = 4096

  /**
   * Algorithm used for message signing.
   */
  val SignAlgorithm = "SHA256withRSA"

  /**
   * Algorithm used for symmetric crypto cipher.
   */
  val SymmetricCipherAlgorithm = "AES"

  /**
   * Algorithm used for symmetric message encryption.
   */
  val SymmetricKeyAlgorithm = "AES"

  /**
   * Length of the symmetric message encryption key in bits.
   */
  val SymmetricKeyLength = 128

  /**
   * Algorithm used to hash PublicKey and get the address.
   */
  val KeyHashAlgorithm = "SHA-256"

  private val LocalAddressKey = "local_address"

  private val PrivateKeyAlias = "local-private"

  private val PublicKeyAlias = "local-public"

}

/**
 * Handles all cryptography related operations.
 *
 * @param keyFolder Folder where private and public keys are stored.
 */
class Crypto(settings: Settings, keyFolder: File) {

  private val Tag = "Crypto"

  /**
   * Generates a new key pair using [[KeyAlgorithm]] with [[KeySize]] bits and stores the keys.
   *
   * Does nothing if the key pair already exists.
   */
  def generateLocalKeys(): Unit = {
    if (localKeysExist)
      return

    var address: Address = null
    var keyPair: KeyPair = null
    do {
      val keyGen = KeyPairGenerator.getInstance(KeyAlgorithm)
      keyGen.initialize(KeySize)
      keyPair = keyGen.genKeyPair()

      address = calculateAddress(keyPair.getPublic)

      // The hash must have at least one bit set to not collide with the broadcast address.
    } while(address == Address.Broadcast || address == Address.Null)

    settings.put(LocalAddressKey, address.toString)

    saveKey(PrivateKeyAlias, keyPair.getPrivate)
    saveKey(PublicKeyAlias, keyPair.getPublic)
    Log.i(Tag, "Generated cryptographic keys, address is " + address)
  }

  /**
   * Returns true if we have a public key stored for the given device.
   */
  def havePublicKey(address: Address): Boolean = new File(keyFolder, address.toString).exists()

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
    val sig = Signature.getInstance(SignAlgorithm)
    val key = loadKey(PrivateKeyAlias, classOf[PrivateKey])
    sig.initSign(key)
    sig.update(msg.body.write)
    new Message(msg.header, new CryptoData(Option(sig.sign), None), msg.body)
  }

  def verify(msg: Message, key: PublicKey = null): Boolean = {
    val publicKey =
      if (key != null) key
      else loadKey(msg.header.origin.toString, classOf[PublicKey])
    val sig = Signature.getInstance(SignAlgorithm)
    sig.initVerify(publicKey)
    sig.update(msg.body.write)
    sig.verify(msg.crypto.signature.get)
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

    keyFolder.mkdirs()
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

  def encrypt(msg: Message, key: PublicKey = null): Message = {
    assert(msg.crypto.signature.isDefined, "Message must be signed before encryption")

    // Symmetric encryption of data
    val secretKey = makeSecretKey()
    val symmetricCipher = Cipher.getInstance(SymmetricCipherAlgorithm)
    symmetricCipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val encrypted = new EncryptedBody(copyThroughCipher(symmetricCipher, msg.body.write))

    // Asymmetric encryption of secret key
    val publicKey =
      if (key != null) key
      else loadKey(msg.header.target.toString, classOf[PublicKey])
    val asymmetricCipher = Cipher.getInstance(KeyAlgorithm)
    asymmetricCipher.init(Cipher.WRAP_MODE, publicKey)

    new Message(msg.header,
      new CryptoData(msg.crypto.signature, Option(asymmetricCipher.wrap(secretKey))), encrypted)
  }

  def decrypt(msg: Message): Message = {
    // Asymmetric decryption of secret key
    val asymmetricCipher = Cipher.getInstance(KeyAlgorithm)
    asymmetricCipher.init(Cipher.UNWRAP_MODE, loadKey(PrivateKeyAlias, classOf[PrivateKey]))
    val key = asymmetricCipher.unwrap(msg.crypto.key.get, SymmetricKeyAlgorithm, Cipher.SECRET_KEY)

    // Symmetric decryption of data
    val symmetricCipher = Cipher.getInstance(SymmetricCipherAlgorithm)
    symmetricCipher.init(Cipher.DECRYPT_MODE, key)
    val decrypted = copyThroughCipher(symmetricCipher, msg.body.asInstanceOf[EncryptedBody].data)
    val body = msg.header.asInstanceOf[ContentHeader].contentType match {
      case Text.Type              => Text.read(decrypted)
      case UserInfo.Type          => UserInfo.read(decrypted)
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
    val b = new Array[Byte](1024)
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
    val kgen = KeyGenerator.getInstance(SymmetricCipherAlgorithm)
    kgen.init(SymmetricKeyLength)
    val key = kgen.generateKey()
    new SecretKeySpec(key.getEncoded, SymmetricKeyAlgorithm)
  }

  /**
   * Hashes the given public key and returns the hash as address.
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
