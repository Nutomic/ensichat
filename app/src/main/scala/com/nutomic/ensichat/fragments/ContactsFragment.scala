package com.nutomic.ensichat.fragments

import android.app.Fragment
import android.content.{Context, Intent, ComponentName, ServiceConnection}
import android.os.{IBinder, Bundle}
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.{ArrayAdapter, ListView}

import com.nutomic.ensichat.R
import com.nutomic.ensichat.bluetooth.ChatService.DeviceListener
import com.nutomic.ensichat.bluetooth.{ChatServiceBinder, ChatService, Device}
import com.nutomic.ensichat.util.DevicesAdapter

class ContactsFragment extends Fragment with DeviceListener {

  private var mListView: ListView = _

  private var mChatService: ChatService = _

  private final val mChatServiceConnection: ServiceConnection = new ServiceConnection {
    override def onServiceConnected(componentName: ComponentName, iBinder: IBinder): Unit = {
      val binder: ChatServiceBinder = iBinder.asInstanceOf[ChatServiceBinder]
      mChatService = binder.getService()
      mChatService.registerDeviceListener(ContactsFragment.this)
    }

    override def onServiceDisconnected(componentName: ComponentName): Unit = {
      mChatService = null
    }
  }

  private var mAdapter: ArrayAdapter[Device] = _

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
        savedInstanceState: Bundle): View = {
    val view: View =  inflater.inflate(R.layout.fragment_contacts, container, false)
    mListView = view.findViewById(android.R.id.list).asInstanceOf[ListView]
    mAdapter = new DevicesAdapter(getActivity)
    mListView.setAdapter(mAdapter)
    return view
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    getActivity.bindService(new Intent(getActivity, classOf[ChatService]),
      mChatServiceConnection, Context.BIND_AUTO_CREATE)
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    getActivity.unbindService(mChatServiceConnection)
  }

  override def onDeviceConnected(device: Device): Unit = {
    mAdapter.add(device)
  }

}
