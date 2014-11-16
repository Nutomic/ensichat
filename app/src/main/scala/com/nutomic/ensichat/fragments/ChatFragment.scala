package com.nutomic.ensichat.fragments

import java.util.Date

import android.app.ListFragment
import android.os.Bundle
import android.view.View.OnClickListener
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, ViewGroup}
import android.widget.TextView.OnEditorActionListener
import android.widget._
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.EnsiChatActivity
import com.nutomic.ensichat.bluetooth.ChatService.OnMessageReceivedListener
import com.nutomic.ensichat.bluetooth.{ChatService, Device}
import com.nutomic.ensichat.messages.{Message, TextMessage}
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

  override def onActivityCreated(savedInstanceState: Bundle): Unit = {
    super.onActivityCreated(savedInstanceState)

    val activity = getActivity.asInstanceOf[EnsiChatActivity]
    activity.runOnServiceConnected(() => {
      chatService = activity.service

      // Read local device ID from service,
      adapter = new MessagesAdapter(getActivity, chatService.localDeviceId)
      chatService.registerMessageListener(ChatFragment.this)
      onMessageReceived(chatService.database.getMessages(device, 10))

      if (listView != null) {
        listView.setAdapter(adapter)
      }
    })
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

    if (savedInstanceState != null) {
      device = new Device.ID(savedInstanceState.getString("device"))
    }
  }

  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    outState.putString("device", device.toString)
  }

  /**
   * Send message if send button was clicked.
   */
  override def onClick(view: View): Unit = {
    view.getId match {
      case R.id.send =>
        val text: String = messageText.getText.toString
        if (!text.isEmpty) {
          if (!chatService.isConnected(device)) {
            Toast.makeText(getActivity, R.string.contact_offline_toast, Toast.LENGTH_SHORT).show()
            return
          }
          chatService.send(
            new TextMessage(chatService.localDeviceId, device, new Date(), text.toString))
          messageText.getText.clear()
        }
    }
  }

  /**
   * Displays new messages in UI.
   */
  override def onMessageReceived(messages: SortedSet[Message]): Unit = {
    messages.filter(m => m.sender == device || m.receiver == device)
      .filter(_.isInstanceOf[TextMessage])
      .foreach(m => adapter.add(m.asInstanceOf[TextMessage]))
  }

  /**
   * Returns the device that this fragment shows chats for.
   */
  def getDevice = this.device

}
