package com.nutomic.ensichat.fragments

import java.util.Date

import android.app.ListFragment
import android.content.{ComponentName, Context, Intent, ServiceConnection}
import android.os.{Bundle, IBinder}
import android.view.View.OnClickListener
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, ViewGroup}
import android.widget.TextView.OnEditorActionListener
import android.widget._
import com.nutomic.ensichat.R
import com.nutomic.ensichat.bluetooth.ChatService.OnMessageReceivedListener
import com.nutomic.ensichat.bluetooth.{ChatService, ChatServiceBinder, Device}
import com.nutomic.ensichat.messages.TextMessage
import com.nutomic.ensichat.util.MessagesAdapter

import scala.collection.SortedSet

/**
 * Represents a single chat with another specific device.
 */
class ChatFragment extends ListFragment with OnClickListener
    with OnMessageReceivedListener {

  def this(device: Device.ID) {
    this
    this.device = device
  }

  private var device: Device.ID = _

  private var chatService: ChatService = _

  private var sendButton: Button = _

  private var messageText: EditText = _

  private var listView: ListView = _

  private var adapter: ArrayAdapter[TextMessage] = _

  private final val mChatServiceConnection: ServiceConnection = new ServiceConnection {
    override def onServiceConnected(componentName: ComponentName, iBinder: IBinder): Unit = {
      val binder: ChatServiceBinder = iBinder.asInstanceOf[ChatServiceBinder]
      chatService = binder.getService
      // Read local device ID from service,
      adapter = new MessagesAdapter(getActivity, chatService.localDeviceId)
      chatService.registerMessageListener(device, ChatFragment.this)
      if (listView != null) {
        listView.setAdapter(adapter)
      }
    }

    override def onServiceDisconnected(componentName: ComponentName): Unit = {
      chatService = null
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
        savedInstanceState: Bundle): View = {
    val view: View =  inflater.inflate(R.layout.fragment_chat, container, false)
    sendButton = view.findViewById(R.id.send).asInstanceOf[Button]
    sendButton.setOnClickListener(this)
    messageText = view.findViewById(R.id.message).asInstanceOf[EditText]
    messageText.setOnEditorActionListener(new OnEditorActionListener {
      override def onEditorAction(view: TextView, actionId: Int, event: KeyEvent): Boolean = {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
          onClick(sendButton)
          true
        } else
          false
      }
    })
    listView = view.findViewById(android.R.id.list).asInstanceOf[ListView]
    listView.setAdapter(adapter)
    view
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    getActivity.bindService(new Intent(getActivity, classOf[ChatService]),
      mChatServiceConnection, Context.BIND_AUTO_CREATE)

    if (savedInstanceState != null) {
      device = new Device.ID(savedInstanceState.getString("device"))
    }
  }

  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    outState.putString("device", device.toString)
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    getActivity.unbindService(mChatServiceConnection)
  }

  /**
   * Send message if send button was clicked.
   */
  override def onClick(view: View): Unit = {
    view.getId match {
      case R.id.send =>
        val text: String = messageText.getText.toString
        if (!text.isEmpty) {
          chatService.send(
            new TextMessage(chatService.localDeviceId, device, text.toString, new Date()))
          messageText.getText.clear()
        }
    }
  }

  /**
   * Displays new messages in UI.
   */
  override def onMessageReceived(messages: SortedSet[TextMessage]): Unit = {
    messages.foreach(m => adapter.add(m))
  }

  /**
   * Returns the device that this fragment shows chats for.
   */
  def getDevice = this.device

}
