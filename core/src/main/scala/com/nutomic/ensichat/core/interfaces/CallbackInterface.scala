package com.nutomic.ensichat.core.interfaces

import com.nutomic.ensichat.core.messages.Message

trait CallbackInterface {

  def onMessageReceived(msg: Message): Unit

  def onConnectionsChanged(): Unit

  def onContactsUpdated(): Unit

}
