package com.nutomic.ensichat.protocol

import android.test.AndroidTestCase
import com.nutomic.ensichat.protocol.AddressTest._
import junit.framework.Assert._

object AddressTest {

  val a1 = new Address("A51B74475EE622C3C924DB147668F85E024CA0B44CA146B5E3D3C31A54B34C1E")

  val a2 = new Address("222229685A73AB8F2F853B3EA515633B7CD5A6ABDC3210BC4EF38F955A14AAF6")

  val a3 = new Address("3333359893F8810C4024CFC951374AABA1F4DE6347A3D7D8E44918AD1FF2BA36")

  val a4 = new Address("4444459893F8810C4024CFC951374AABA1F4DE6347A3D7D8E44918AD1FF2BA36")

  val Addresses = Set(a1, a1Dashed, a2, a3, a4, Address.Broadcast, Address.Null)

  val a1Binary: Array[Byte] = Array(-91, 27, 116, 71, 94, -26, 34, -61, -55, 36, -37, 20, 118, 104,
    -8, 94, 2, 76, -96, -76, 76, -95, 70, -75, -29, -45, -61, 26, 84, -77, 76, 30).map(_.toByte)

  val a1Dashed =
    new Address("A51B7447-5EE622C3-C924DB14-7668F85E-024CA0B4-4CA146B5-E3D3C31A-54B34C1E")

}

class AddressTest extends AndroidTestCase {

  def testEncode(): Unit = {
    Addresses.foreach{a =>
      val base32 = a.toString
      val read = new Address(base32)
      assertEquals(a, read)
      assertEquals(a.hashCode, read.hashCode)
    }

    assertEquals(a1, new Address(a1Binary))
    assertEquals(a1Binary.deep, a1.bytes.deep)
  }

  def testDashes(): Unit = {
    assertEquals(a1, a1Dashed)
  }

}