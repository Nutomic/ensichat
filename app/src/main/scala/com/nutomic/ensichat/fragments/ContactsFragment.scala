package com.nutomic.ensichat.fragments

import java.io.File

import android.app.ListFragment
import android.content.{IntentFilter, Context, BroadcastReceiver, Intent}
import android.net.Uri
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.view._
import android.widget.ListView
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.{AddContactsActivity, EnsichatActivity, MainActivity, SettingsActivity}
import com.nutomic.ensichat.protocol.ChatService
import com.nutomic.ensichat.util.Database
import com.nutomic.ensichat.views.UsersAdapter
import scala.collection.JavaConversions._

/**
 * Lists all nearby, connected devices.
 */
class ContactsFragment extends ListFragment {

  private lazy val adapter = new UsersAdapter(getActivity)

  private lazy val database = new Database(getActivity)

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    setListAdapter(adapter)
    setHasOptionsMenu(true)

    getActivity.asInstanceOf[EnsichatActivity].runOnServiceConnected(() => {
      database.getContacts.foreach(adapter.add)
    })
    LocalBroadcastManager.getInstance(getActivity)
      .registerReceiver(onContactsUpdatedListener, new IntentFilter(Database.ActionContactsUpdated))
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    LocalBroadcastManager.getInstance(getActivity).unregisterReceiver(onContactsUpdatedListener)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
                            savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_contacts, container, false)

  override def onCreateOptionsMenu(menu: Menu, inflater: MenuInflater): Unit = {
    super.onCreateOptionsMenu(menu, inflater)
    inflater.inflate(R.menu.main, menu)
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {
    case R.id.add_contact =>
      startActivity(new Intent(getActivity, classOf[AddContactsActivity]))
      true
    case R.id.share_app =>
      val pm = getActivity.getPackageManager
      val ai = pm.getInstalledApplications(0).find(_.sourceDir.contains(getActivity.getPackageName))
      val intent = new Intent()
      intent.setAction(Intent.ACTION_SEND)
      intent.setType("*/*")
      intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(ai.get.sourceDir)))
      startActivity(intent)
      true
    case R.id.settings =>
      startActivity(new Intent(getActivity, classOf[SettingsActivity]))
      true
    case R.id.exit =>
      getActivity.stopService(new Intent(getActivity, classOf[ChatService]))
      getActivity.finish()
      true
    case _ =>
      super.onOptionsItemSelected(item)
  }

  /**
   * Opens a chat with the clicked device.
   */
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long): Unit =
    getActivity.asInstanceOf[MainActivity].openChat(adapter.getItem(position).address)

  private val onContactsUpdatedListener = new BroadcastReceiver() {
    override def onReceive(context: Context, intent: Intent): Unit = {
      getActivity.runOnUiThread(new Runnable {
        override def run(): Unit = {
          adapter.clear()
          database.getContacts.foreach(adapter.add)
        }
      })
    }
  }

}
