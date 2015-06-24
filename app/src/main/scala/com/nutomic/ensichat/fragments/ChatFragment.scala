package com.nutomic.ensichat.fragments

import android.app.ListFragment
import android.os.Bundle
import android.view.View.OnClickListener
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, ViewGroup}
import android.widget.TextView.OnEditorActionListener
import android.widget._
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.EnsichatActivity
import com.nutomic.ensichat.protocol.ChatService.OnMessageReceivedListener
import com.nutomic.ensichat.protocol.body.Text
import com.nutomic.ensichat.protocol.{Message, Address, ChatService}
import com.nutomic.ensichat.util.{Database, MessagesAdapter}

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

  private lazy val database = new Database(getActivity)

  private var address: Address = _

  private var chatService: ChatService = _

  private var sendButton: Button = _

  private var messageText: EditText = _

  private var listView: ListView = _

  private var adapter: ArrayAdapter[Message] = _

  override def onActivityCreated(savedInstanceState: Bundle): Unit = {
    super.onActivityCreated(savedInstanceState)

    val activity = getActivity.asInstanceOf[EnsichatActivity]
    activity.runOnServiceConnected(() => {
      chatService = activity.service

      database.getContact(address).foreach(c => getActivity.setTitle(c.name))

      // Read local device ID from service,
      adapter = new MessagesAdapter(getActivity, address)
      chatService.registerMessageListener(ChatFragment.this)
      database.getMessages(address, 15).foreach(adapter.add)

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

    if (savedInstanceState != null)
      address = new Address(savedInstanceState.getByteArray("device"))
  }

  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    outState.putByteArray("device", address.bytes)
  }

  /**
   * Send message if send button was clicked.
   */
  override def onClick(view: View): Unit = view.getId match {
    case R.id.send =>
      val text = messageText.getText.toString.trim
      if (!text.isEmpty) {
        val message = new Text(text.toString)
        chatService.sendTo(address, message)
        messageText.getText.clear()
      }
  }

  /**
   * Displays new messages in UI.
   */
  override def onMessageReceived(msg: Message): Unit = {
    if (!Set(msg.header.origin, msg.header.target).contains(address))
      return

    msg.body match {
      case _: Text => adapter.add(msg)
      case _ =>
    }
  }

}
