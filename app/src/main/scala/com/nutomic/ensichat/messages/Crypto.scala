package com.nutomic.ensichat.messages

import java.io._
import java.security._
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{Cipher, CipherOutputStream, KeyGenerator, SecretKey}

import android.content.Context
import android.preference.PreferenceManager
import android.util.Log
import com.nutomic.ensichat.aodvv2.Address
import com.nutomic.ensichat.messages.Crypto._
import com.nutomic.ensichat.util.PRNGFixes

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
  val SymmetricKeyAlgorithm = "AES/CBC/PKCS5Padding"

  /**
   * Algorithm used to hash PublicKey and get the address.
   */
  val KeyHashAlgorithm = "SHA-256"

  private val LocalAddressKey = "local_address"

  /**
   * Returns the address of the local node.
   */
  def getLocalAddress(context: Context) = new Address(
      PreferenceManager.getDefaultSharedPreferences(context).getString(LocalAddressKey, null))

}

/**
 * Handles all cryptography related operations.
 *
 * @note We can't use [[KeyStore]], because it requires certificates, and does not work for
 *       private keys
 */
class Crypto(Context: Context) {

  private val Tag = "Crypto"

  private val PrivateKeyAlias = "local-private"

  private val PublicKeyAlias = "local-public"

  PRNGFixes.apply()

  /**
   * Generates a new key pair using [[KeyAlgorithm]] with [[KeySize]] bits and stores the keys.
   */
  def generateLocalKeys(): Unit = {
    var address: Address = null
    var keyPair: KeyPair = null
    do {
      val keyGen = KeyPairGenerator.getInstance(KeyAlgorithm)
      keyGen.initialize(KeySize)
      keyPair = keyGen.genKeyPair()

      address = calculateAddress(keyPair.getPublic)

      // The hash must have at least one bit set to not collide with the broadcast address.
    } while(address == Address.Broadcast || address == Address.Null)

    PreferenceManager.getDefaultSharedPreferences(Context)
      .edit()
      .putString(Crypto.LocalAddressKey, address.toString)
      .commit()

    saveKey(PrivateKeyAlias, keyPair.getPrivate)
    saveKey(PublicKeyAlias, keyPair.getPublic)
    Log.i(Tag, "Generating cryptographic keys, address is " + address)
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
  def getPublicKey(address: Address): PublicKey = {
    loadKey(address.toString, classOf[PublicKey])
  }

  /**
   * Adds a new public key for a remote device.
   *
   * If a key for the device already exists, nothing is done.
   *
   * @param address The device to which the key belongs.
   * @param key The new key to add.
   */
  def addPublicKey(address: Address, key: PublicKey): Unit = {
    if (!havePublicKey(address)) {
      saveKey(address.toString, key)
    } else {
      Log.i(Tag, "Already have key for " + address.toString + ", not overwriting")
    }
  }

  /**
   * Checks if the message was properly signed.
   *
   * This is done by signing the output of [[Message.write()]] called with an empty signature.
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
    sig.initVerify(publicKey)
    sig.update(message.write(Array[Byte]()))
    sig.verify(signature)
  }

  /**
   * Returns a cryptographic signature for the given message (using local private key).
   *
   * This is done by signing the output of [[Message.write()]] called with an empty signature.
   */
  def calculateSignature(message: Message): Array[Byte] = {
    val sig = Signature.getInstance(SignAlgorithm)
    val key = loadKey(PrivateKeyAlias, classOf[PrivateKey])
    sig.initSign(key)
    sig.update(message.write(Array[Byte]()))
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
  private def keyFolder = new File(Context.getFilesDir, "keys")

  /**
   * Encrypts data for the given receiver.
   *
   * @param receiver The device that should be able to decrypt this message.
   * @param data The message to encrypt.
   * @param key Optional RSA public key to use for encryption.
   * @return Pair of AES encrypted data and RSA encrypted AES key.
   */
  def encrypt(receiver: Address, data: Array[Byte], key: PublicKey = null):
      (Array[Byte], Array[Byte]) = {
    // Symmetric encryption of data
    val secretKey = makeSecretKey()
    val symmetricCipher = Cipher.getInstance(SymmetricCipherAlgorithm)
    symmetricCipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val encryptedData = copyThroughCipher(symmetricCipher, data)

    // Asymmetric encryption of secret key
    val publicKey =
      if (key != null) key
      else loadKey(receiver.toString, classOf[PublicKey])
    val asymmetricCipher = Cipher.getInstance(KeyAlgorithm)
    asymmetricCipher.init(Cipher.WRAP_MODE, publicKey)

    (encryptedData, asymmetricCipher.wrap(secretKey))
  }

  /**
   * Decrypts the output of [[encrypt]].
   *
   * @param data The AES encrypted data to decrypt.
   * @param key The RSA encrypted AES key used to encrypt data.
   * @return The plain text data.
   */
  def decrypt(data: Array[Byte], key: Array[Byte]): Array[Byte] = {
    // Asymmetric decryption of secret key
    val asymmetricCipher = Cipher.getInstance(KeyAlgorithm)
    asymmetricCipher.init(Cipher.UNWRAP_MODE, loadKey(PrivateKeyAlias, classOf[PrivateKey]))
    val secretKey = asymmetricCipher.unwrap(key, SymmetricKeyAlgorithm, Cipher.SECRET_KEY)

    // Symmetric decryption of data
    val symmetricCipher = Cipher.getInstance(SymmetricCipherAlgorithm)
    symmetricCipher.init(Cipher.DECRYPT_MODE, secretKey)
    val dec = copyThroughCipher(symmetricCipher, data)
    dec
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
    kgen.init(256)
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

  def getLocalAddress = Crypto.getLocalAddress(Context)

}
