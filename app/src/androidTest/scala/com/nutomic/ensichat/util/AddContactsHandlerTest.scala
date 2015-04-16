package com.nutomic.ensichat.util

import android.content.Context
import android.test.AndroidTestCase
import com.nutomic.ensichat.protocol.body.{RequestAddContact, ResultAddContact}
import com.nutomic.ensichat.protocol.header.ContentHeader
import com.nutomic.ensichat.protocol.{Address, Crypto, Message, UserTest}
import junit.framework.Assert._

class AddContactsHandlerTest extends AndroidTestCase {

  private class MockContext(context: Context) extends DatabaseTest.MockContext(context) {
    override def getResources = context.getResources
    override def getSystemService(name: String) = context.getSystemService(name)
    override def getPackageName = context.getPackageName
  }

  private lazy val handler =
    new AddContactsHandler(context, (address: Address) => UserTest.u1, UserTest.u1.address)

  private lazy val context = new MockContext(getContext)

  private lazy val database = new Database(context)

  private lazy val crypto = new Crypto(getContext)

  private lazy val header1 =
    new ContentHeader(UserTest.u1.address, crypto.localAddress, 0, RequestAddContact.Type, 0)
  private lazy val header2 =
    new ContentHeader(UserTest.u1.address, crypto.localAddress, 0, ResultAddContact.Type, 0)
  private lazy val header3 =
    new ContentHeader(crypto.localAddress, UserTest.u1.address, 0, ResultAddContact.Type, 0)

  override def tearDown(): Unit = {
    super.tearDown()
    database.close()
    context.deleteDbFile()
  }

  def testAddContact(): Unit = {
    assertFalse(database.getContact(UserTest.u1.address).isDefined)
    handler.onMessageReceived(new Message(header1, new RequestAddContact))
    handler.onMessageReceived(new Message(header2, new ResultAddContact(true)))
    handler.onMessageReceived(new Message(header3, new ResultAddContact(true)))
    assertTrue(database.getContact(UserTest.u1.address).isDefined)
  }

  def testAddContactDenied(): Unit = {
    assertFalse(database.getContact(UserTest.u1.address).isDefined)
    handler.onMessageReceived(new Message(header1, new RequestAddContact))
    handler.onMessageReceived(new Message(header2, new ResultAddContact(true)))
    handler.onMessageReceived(new Message(header3, new ResultAddContact(false)))
    assertFalse(database.getContact(UserTest.u1.address).isDefined)
  }

}
