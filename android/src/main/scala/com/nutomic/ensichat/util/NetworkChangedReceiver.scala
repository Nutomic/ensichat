package com.nutomic.ensichat.util

import android.content.{Intent, Context, BroadcastReceiver}
import android.net.ConnectivityManager
import com.nutomic.ensichat.service.ChatService

class NetworkChangedReceiver extends BroadcastReceiver {

  override def onReceive(context: Context, intent: Intent): Unit = {
    val intent = new Intent(context, classOf[ChatService])
    intent.setAction(ChatService.ActionNetworkChanged)
    context.startService(intent)
  }

}
