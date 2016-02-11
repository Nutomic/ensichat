package com.nutomic.ensichat.service

import android.content.{BroadcastReceiver, Context, Intent}
import android.preference.PreferenceManager

/**
 * Starts [[ChatService]] on boot if preference is enabled.
 */
class BootReceiver extends BroadcastReceiver {

  override def onReceive(context: Context, intent: Intent): Unit = {
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    if (sp.getBoolean("start_on_boot", false)) {
      context.startService(new Intent(context, classOf[ChatService]))
    }
  }

}
