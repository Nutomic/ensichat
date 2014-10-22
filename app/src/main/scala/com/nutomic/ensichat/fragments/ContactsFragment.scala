package com.nutomic.ensichat.fragments

import android.app.ListFragment
import android.content.{ComponentName, Context, Intent, ServiceConnection}
import android.os.{Bundle, IBinder}
import android.util.Log
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{ArrayAdapter, ListView}
import com.nutomic.ensichat.bluetooth.{ChatService, ChatServiceBinder, Device}
import com.nutomic.ensichat.util.DevicesAdapter
import com.nutomic.ensichat.{Message, R}

class ContactsFragment extends ListFragment {

  private var chatService: ChatService = _

  private final val mChatServiceConnection: ServiceConnection = new ServiceConnection {
    override def onServiceConnected(componentName: ComponentName, iBinder: IBinder): Unit = {
      val binder: ChatServiceBinder = iBinder.asInstanceOf[ChatServiceBinder]
      chatService = binder.getService()
      chatService.registerDeviceListener(onDeviceConnected)
    }

    override def onServiceDisconnected(componentName: ComponentName): Unit = {
      chatService = null
    }
  }

  private var adapter: ArrayAdapter[Device] = _

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
        savedInstanceState: Bundle): View = {
    val view: View =  inflater.inflate(R.layout.fragment_contacts, container, false)
    return view
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    adapter = new DevicesAdapter(getActivity)
    setListAdapter(adapter)
    getActivity.bindService(new Intent(getActivity, classOf[ChatService]),
      mChatServiceConnection, Context.BIND_AUTO_CREATE)
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    getActivity.unbindService(mChatServiceConnection)
    chatService.unregisterDeviceListener(onDeviceConnected)
  }

  /**
   * Displays all connected devices in the listview.
   */
  def onDeviceConnected(devices: Map[Device.ID, Device]): Unit = {
    val filtered = devices.filter{ case (_, d) => d.connected }
    getActivity.runOnUiThread(new Runnable {
      override def run(): Unit  = {
        adapter.clear()
        filtered.values.foreach(f => adapter.add(f))
      }
    })
  }

  /**
   * Sends a ping message to the clicked device.
   */
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long): Unit = {
    chatService.send(adapter.getItem(position).id, new Message("Ping"))
  }

}
