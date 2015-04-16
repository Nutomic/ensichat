package com.nutomic.ensichat.protocol.body

import android.test.AndroidTestCase
import junit.framework.Assert._


class ResultAddContactTest extends AndroidTestCase {

  def testWriteRead(): Unit = {
    Array(true, false).foreach { a =>
      val rac = new ResultAddContact(a)
      val bytes = rac.write
      val read = ResultAddContact.read(bytes)
      assertEquals(a, read.accepted)
    }
  }

}
