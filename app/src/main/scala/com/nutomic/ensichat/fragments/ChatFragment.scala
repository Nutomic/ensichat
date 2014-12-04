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
import com.nutomic.ensichat.aodvv2.Address
import com.nutomic.ensichat.bluetooth.ChatService
import com.nutomic.ensichat.bluetooth.ChatService.OnMessageReceivedListener
import com.nutomic.ensichat.messages.{Crypto, Message, TextMessage}
import com.nutomic.ensichat.util.MessagesAdapter

import scala.collection.SortedSet

/**
 * Represents a single chat with another specific device.
 */
class ChatFragment extends ListFragment with OnClickListener
    with OnMessageReceivedListener {

  /**
   * Fragments need to have a default constructor, so this is optional.
   */
  def this(address: Address) {
    this
    this.address = address
  }

  private var address: Address = _

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
      adapter = new MessagesAdapter(getActivity, address)
      chatService.registerMessageListener(ChatFragment.this)
      onMessageReceived(chatService.database.getMessages(address, 15))

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
      address = new Address(savedInstanceState.getByteArray("device"))
    }
  }

  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    outState.putByteArray("device", address.Bytes)
  }

  /**
   * Send message if send button was clicked.
   */
  override def onClick(view: View): Unit = view.getId match {
    case R.id.send =>
      val text = messageText.getText.toString.trim
      if (!text.isEmpty) {
        val message =
          new TextMessage(Crypto.getLocalAddress(getActivity), address, new Date(), text.toString)
        chatService.send(message)
        adapter.add(message)
        messageText.getText.clear()
      }
  }

  /**
   * Displays new messages in UI.
   */
  override def onMessageReceived(messages: SortedSet[Message]): Unit = {
    messages.filter(m => m.sender == address || m.receiver == address)
      .filter(_.isInstanceOf[TextMessage])
      .foreach(m => adapter.add(m.asInstanceOf[TextMessage]))
  }

  /**
   * Returns the device that this fragment shows chats for.
   */
  def getDevice = address

}
