package com.nutomic.ensichat.protocol.body

import android.test.AndroidTestCase
import junit.framework.Assert


class PaymentInformationTest extends AndroidTestCase {

  def testWriteRead(): Unit = {
    val pi = new PaymentInformation("testmessage".getBytes)
    val bytes = pi.write
    val body = PaymentInformation.read(bytes)
    Assert.assertEquals(pi, body)
  }

}
