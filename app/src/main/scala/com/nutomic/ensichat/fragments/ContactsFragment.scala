package com.nutomic.ensichat.fragments

import android.app.ListFragment
import android.content.{ComponentName, Context, Intent, ServiceConnection}
import android.os.{Bundle, IBinder}
import android.view._
import android.widget.{ArrayAdapter, ListView}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.{SettingsActivity, EnsiChatActivity, MainActivity}
import com.nutomic.ensichat.bluetooth.{ChatService, ChatServiceBinder, Device}
import com.nutomic.ensichat.util.{MessagesAdapter, DevicesAdapter}

/**
 * Lists all nearby, connected devices.
 */
class ContactsFragment extends ListFragment with ChatService.OnConnectionChangedListener {

  private lazy val adapter = new DevicesAdapter(getActivity)

  override def onActivityCreated(savedInstanceState: Bundle): Unit = {
    super.onActivityCreated(savedInstanceState)

    val activity = getActivity.asInstanceOf[EnsiChatActivity]
    activity.runOnServiceConnected(() => {
      activity.service.registerConnectionListener(ContactsFragment.this)
    })
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
        savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_contacts, container, false)

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    setListAdapter(adapter)
    setHasOptionsMenu(true)
  }

  override def onCreateOptionsMenu(menu: Menu, inflater: MenuInflater): Unit = {
    super.onCreateOptionsMenu(menu, inflater)
    inflater.inflate(R.menu.main, menu)
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
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
  }

  /**
   * Displays newly connected devices in the list.
   */
  override def onConnectionChanged(devices: Map[Device.ID, Device]): Unit = {
    if (getActivity == null)
      return

    val filtered = devices.filter{ case (_, d) => d.connected }
    getActivity.runOnUiThread(new Runnable {
      override def run(): Unit  = {
        adapter.clear()
        filtered.values.foreach(f => adapter.add(f))
      }
    })
  }

  /**
   * Opens a chat with the clicked device.
   */
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long): Unit =
    getActivity.asInstanceOf[MainActivity].openChat(adapter.getItem(position).id)

}
