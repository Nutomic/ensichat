package com.nutomic.ensichat.service

import android.app.{Notification, NotificationManager, PendingIntent}
import android.content.{Context, Intent}
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.MainActivity
import com.nutomic.ensichat.core.Message
import com.nutomic.ensichat.core.body.Text
import com.nutomic.ensichat.core.interfaces.SettingsInterface
import com.nutomic.ensichat.service.NotificationHandler._

object NotificationHandler {

  private val NotificationIdRunning = 1

  private val NotificationIdNewMessage = 2

}

/**
 * Displays notifications for new messages and while the app is running.
 */
class NotificationHandler(context: Context) {

  private lazy val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
    .asInstanceOf[NotificationManager]

  def showPersistentNotification(): Unit = {
    val intent = PendingIntent.getActivity(context, 0, new Intent(context, classOf[MainActivity]), 0)
    val notification = new NotificationCompat.Builder(context)
      .setSmallIcon(R.drawable.ic_launcher)
      .setContentTitle(context.getString(R.string.app_name))
      .setContentIntent(intent)
      .setOngoing(true)
      .setPriority(Notification.PRIORITY_MIN)
      .build()
    notificationManager.notify(NotificationIdRunning, notification)
  }

  def cancelPersistentNotification() =  notificationManager.cancel(NotificationIdRunning)

  def onMessageReceived(msg: Message): Unit = msg.body match {
    case text: Text =>
      if (msg.header.origin == ChatService.newCrypto(context).localAddress)
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

      notificationManager.notify(NotificationIdNewMessage, notification)
    case _ =>
  }

  /**
   * Returns the default notification options that should be used.
   */
  private def defaults(): Int = {
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    if (sp.getBoolean(SettingsInterface.KeyNotificationSoundsOn, SettingsInterface.DefaultNotificationSoundsOn))
      Notification.DEFAULT_ALL
    else
      Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS
  }

}
