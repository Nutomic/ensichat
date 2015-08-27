package com.nutomic.ensichat.util

import android.app.{NotificationManager, PendingIntent}
import android.content.{Context, Intent}
import android.os.{Handler, Looper}
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.widget.Toast
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.{ConfirmAddContactActivity, MainActivity}
import com.nutomic.ensichat.protocol.body.{RequestAddContact, ResultAddContact}
import com.nutomic.ensichat.protocol.{Address, Message, User}

/**
 * Handles [[RequestAddContact]] and [[ResultAddContact]] messages, adds new contacts.
 *
 * @param getUser Returns info about a given address.
 * @param localAddress Address of the local device.
 */
class AddContactsHandler(context: Context, getUser: (Address) => User, localAddress: Address) {

  private val Tag = "AddContactsHandler"

  private val notificationIdAddContactGenerator = Stream.from(100).iterator

  private lazy val database = new Database(context)

  private val notificationManager =
    context.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]

  private var currentlyAdding = Map[Address, AddContactInfo]()

  private case class AddContactInfo(localConfirmed: Boolean, remoteConfirmed: Boolean,
                                    notificationId: Int)

  def onMessageReceived(msg: Message): Unit = {
    val remote =
      if (msg.header.origin == localAddress)
        msg.header.target
      else
        msg.header.origin

    msg.body match {
      case _: RequestAddContact =>
        // Don't show notification if we are already adding the contact.
        // Can happen when both users click on each other to add.
        if (currentlyAdding.keySet.contains(remote)) {
          notificationManager.cancel(currentlyAdding(remote).notificationId)
          return
        }

        // Notification ID is unused for requests coming from local device, but that doesn't matter.
        val notificationId = notificationIdAddContactGenerator.next()
        currentlyAdding += (remote -> new AddContactInfo(false, false, notificationId))

        // Don't show notification for requests coming from local device.
        if (msg.header.origin == localAddress)
          return

        Log.i(Tag, "Remote device " + remote + " wants to add us as a contact")

        val intent = new Intent(context, classOf[ConfirmAddContactActivity])
        intent.putExtra(ConfirmAddContactActivity.ExtraContactAddress, msg.header.origin.toString)
        val pi = PendingIntent.getActivity(context, 0, intent,
          PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = new NotificationCompat.Builder(context)
          .setContentTitle(context.getString(R.string.notification_friend_request, getUser(remote)))
          .setSmallIcon(R.drawable.ic_launcher)
          .setContentIntent(pi)
          .setAutoCancel(true)
          .build()

        notificationManager.notify(notificationId, notification)
      case res: ResultAddContact =>
        if (!currentlyAdding.contains(remote)) {
          Log.w(Tag, "ResultAddContact without previous RequestAddContact, ignoring")
          return
        }

        val previousInfo = currentlyAdding(remote)
        val newInfo =
          if (msg.header.origin == localAddress)
            new AddContactInfo(res.accepted, previousInfo.remoteConfirmed, previousInfo.notificationId)
          else
            new AddContactInfo(previousInfo.localConfirmed, res.accepted, previousInfo.notificationId)
        currentlyAdding += (remote -> newInfo)

        if (res.accepted)
          addContactIfBothConfirmed(remote)
        else {
          currentlyAdding -= remote
          notificationManager.cancel(previousInfo.notificationId)
        }
      case _ =>
    }
  }

  /**
   * Adds the given address as a new contact, if local and remote device sent a [[ResultAddContact]]
   * message with accepted = true.
   */
  private def addContactIfBothConfirmed(address: Address): Unit = {
    val info = currentlyAdding(address)
    val user = getUser(address)
    if (info.localConfirmed && info.remoteConfirmed) {
      Log.i(Tag, "Adding new contact " + user.toString)
      database.addContact(user)
      new Handler(Looper.getMainLooper).post(new Runnable {
        override def run(): Unit = {
          Toast.makeText(context, context.getString(R.string.contact_added, user.name),
                         Toast.LENGTH_SHORT).show()
        }
      })
      val intent = new Intent(context, classOf[MainActivity])
      intent.setAction(MainActivity.ActionOpenChat)
      intent.putExtra(MainActivity.ExtraAddress, address.toString)
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP |
                      Intent.FLAG_ACTIVITY_SINGLE_TOP)
      context.startActivity(intent)
      currentlyAdding -= address
      notificationManager.cancel(info.notificationId)
    }
  }

}
