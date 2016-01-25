package com.nutomic.ensichat.server

import com.nutomic.ensichat.core.interfaces.DatabaseInterface
import com.nutomic.ensichat.core.{Address, Message, User}

class Database extends DatabaseInterface {

  private var contacts = Set[User]()

  def onMessageReceived(msg: Message): Unit = {}

  def getContacts: Set[User] = contacts

  def getContact(address: Address): Option[User] = contacts.find(_.address == address)

  def addContact(contact: User): Unit = contacts += contact

  def updateContact(contact: User): Unit = {
    contacts = contacts.filterNot(_.address == contact.address)
    contacts += contact
  }

}
