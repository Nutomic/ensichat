package com.nutomic.ensichat.util

import android.app.{NotificationManager, Notification, PendingIntent}
import android.content.{Context, Intent}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.MainActivity
import com.nutomic.ensichat.protocol.ChatService.OnMessageReceivedListener
import com.nutomic.ensichat.protocol.Crypto
import com.nutomic.ensichat.protocol.messages.{Text, Message}

/**
 * Displays notifications for new messages.
 */
class NotificationHandler(context: Context) extends OnMessageReceivedListener {

  private val notificationIdNewMessage = 1

  def onMessageReceived(msg: Message): Unit = msg.Body match {
    case text: Text =>
      if (msg.Header.origin == new Crypto(context).localAddress)
        return

      val pi = PendingIntent.getActivity(context, 0, new Intent(context, classOf[MainActivity]), 0)
      val notification = new Notification.Builder(context)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle(context.getString(R.string.notification_message))
        .setContentText(text.text)
        .setDefaults(Notification.DEFAULT_ALL)
        .setContentIntent(pi)
        .setAutoCancel(true)
        .build()
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
        .asInstanceOf[NotificationManager]
      nm.notify(notificationIdNewMessage, notification)
    case _ =>
  }

}
