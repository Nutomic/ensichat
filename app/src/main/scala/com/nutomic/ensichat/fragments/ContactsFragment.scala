package com.nutomic.ensichat.fragments

import android.app.ListFragment
import android.content.{ComponentName, Context, Intent, ServiceConnection}
import android.os.{Bundle, IBinder}
import android.view._
import android.widget.{ArrayAdapter, ListView}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.MainActivity
import com.nutomic.ensichat.bluetooth.{ChatService, ChatServiceBinder, Device}
import com.nutomic.ensichat.util.DevicesAdapter

/**
 * Lists all nearby, connected devices.
 */
class ContactsFragment extends ListFragment with ChatService.OnConnectionChangedListener {

  private var chatService: ChatService = _

  private final val ChatServiceConnection: ServiceConnection = new ServiceConnection {
    override def onServiceConnected(componentName: ComponentName, iBinder: IBinder): Unit = {
      val binder: ChatServiceBinder = iBinder.asInstanceOf[ChatServiceBinder]
      chatService = binder.getService
      chatService.registerConnectionListener(ContactsFragment.this)
    }

    override def onServiceDisconnected(componentName: ComponentName): Unit = {
      chatService = null
    }
  }

  private var adapter: ArrayAdapter[Device] = _

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
        savedInstanceState: Bundle): View = {
    val view: View =  inflater.inflate(R.layout.fragment_contacts, container, false)
    view
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    adapter = new DevicesAdapter(getActivity)
    setListAdapter(adapter)
    getActivity.bindService(new Intent(getActivity, classOf[ChatService]),
      ChatServiceConnection, Context.BIND_AUTO_CREATE)
    setHasOptionsMenu(true)
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    getActivity.unbindService(ChatServiceConnection)
  }

  override def onCreateOptionsMenu(menu: Menu, inflater: MenuInflater): Unit = {
    super.onCreateOptionsMenu(menu, inflater)
    inflater.inflate(R.menu.main, menu)
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
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
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long): Unit = {
    getActivity.asInstanceOf[MainActivity].openChat(adapter.getItem(position).id)
  }

}
