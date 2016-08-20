package com.nutomic.ensichat.service

import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import com.nutomic.ensichat.core.interfaces.CallbackInterface
import com.nutomic.ensichat.core.ConnectionHandler
import com.nutomic.ensichat.core.messages.Message
import com.nutomic.ensichat.service.CallbackHandler._

object CallbackHandler {

  val ActionMessageReceived    = "message_received"
  val ActionConnectionsChanged = "connections_changed"
  val ActionContactsUpdated    = "contacts_updated"

  val ExtraMessage             = "extra_message"

}

/**
 * Receives events from [[ConnectionHandler]] and sends them as local broadcasts.
 */
class CallbackHandler(chatService: ChatService, notificationHandler: NotificationHandler)
  extends CallbackInterface {

  def onMessageReceived(msg: Message): Unit = {
    notificationHandler.onMessageReceived(msg)
    val i = new Intent(ActionMessageReceived)
    i.putExtra(ExtraMessage, msg)
    LocalBroadcastManager.getInstance(chatService)
      .sendBroadcast(i)

  }

  def onConnectionsChanged(): Unit = {
    val i = new Intent(ActionConnectionsChanged)
    LocalBroadcastManager.getInstance(chatService)
      .sendBroadcast(i)
    notificationHandler
      .updatePersistentNotification(chatService.getConnectionHandler.connections().size)
  }

  def onContactsUpdated(): Unit = {
    val i = new Intent(ActionContactsUpdated)
    LocalBroadcastManager.getInstance(chatService)
      .sendBroadcast(i)
  }

}
