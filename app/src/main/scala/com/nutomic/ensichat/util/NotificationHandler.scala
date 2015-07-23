package com.nutomic.ensichat.util

import android.app.{Notification, NotificationManager, PendingIntent}
import android.content.{Context, Intent}
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.MainActivity
import com.nutomic.ensichat.protocol.body.Text
import com.nutomic.ensichat.protocol.{Crypto, Message}

object NotificationHandler {

  val NotificationIdRunning = 1

  val NotificationIdNewMessage = 2

}

/**
 * Displays notifications for new messages.
 */
class NotificationHandler(context: Context) {

  def onMessageReceived(msg: Message): Unit = msg.body match {
    case text: Text =>
      if (msg.header.origin == new Crypto(context).localAddress)
        return

      val pi = PendingIntent.getActivity(context, 0, new Intent(context, classOf[MainActivity]), 0)
      val notification = new NotificationCompat.Builder(context)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle(context.getString(R.string.notification_message))
        .setContentText(text.text)
        .setDefaults(defaults())
        .setContentIntent(pi)
        .setAutoCancel(true)
        .build()

      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
        .asInstanceOf[NotificationManager]
      nm.notify(NotificationHandler.NotificationIdNewMessage, notification)
    case _ =>
  }

  /**
   * Returns the default notification options that should be used.
   */
  def defaults(): Int = {
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val defaultSounds = context.getResources.getBoolean(R.bool.default_notification_sounds)
    if (sp.getBoolean("notification_sounds", defaultSounds))
      Notification.DEFAULT_ALL
    else
      Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS
  }

}
