package com.nutomic.ensichat.util

import android.content.{BroadcastReceiver, Context, Intent}
import android.net.ConnectivityManager
import com.nutomic.ensichat.service.ChatService

/**
 * Forwards network changed intents to [[ChatService]].
 *
 * HACK: Because [[ConnectivityManager.CONNECTIVITY_ACTION]] is a sticky intent, and we register it
 *       from Scala, an intent is sent as soon as the receiver is registered. As a workaround, we
 *       ignore the first intent received.
 *       Alternatively, we can register the receiver in the manifest, but that will start the
 *       service (so it only works if the service runs permanently, with no exit).
 */
class NetworkChangedReceiver extends BroadcastReceiver {

  private var isFirstIntent = true

  override def onReceive(context: Context, intent: Intent): Unit = {
    if (isFirstIntent) {
      isFirstIntent = false
      return
    }

    val intent = new Intent(context, classOf[ChatService])
    intent.setAction(ChatService.ActionNetworkChanged)
    context.startService(intent)
  }

}
