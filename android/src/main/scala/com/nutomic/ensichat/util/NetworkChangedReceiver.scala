package com.nutomic.ensichat.util

import android.content.{BroadcastReceiver, Context, Intent}
import com.nutomic.ensichat.service.ChatService

class NetworkChangedReceiver extends BroadcastReceiver {

  override def onReceive(context: Context, intent: Intent): Unit = {
    val intent = new Intent(context, classOf[ChatService])
    intent.setAction(ChatService.ActionNetworkChanged)
    context.startService(intent)
  }

}
