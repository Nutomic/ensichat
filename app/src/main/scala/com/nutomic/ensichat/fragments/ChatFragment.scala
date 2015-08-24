package com.nutomic.ensichat.fragments

import android.app.ListFragment
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.view.View.OnClickListener
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, ViewGroup}
import android.widget.TextView.OnEditorActionListener
import android.widget._
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.EnsichatActivity
import com.nutomic.ensichat.protocol.body.Text
import com.nutomic.ensichat.protocol.{Address, ChatService, Message}
import com.nutomic.ensichat.util.Database
import com.nutomic.ensichat.views.{DatesAdapter, MessagesAdapter}

/**
 * Represents a single chat with another specific device.
 */
class ChatFragment extends ListFragment with OnClickListener {

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

  private var adapter: DatesAdapter = _

  override def onActivityCreated(savedInstanceState: Bundle): Unit = {
    super.onActivityCreated(savedInstanceState)

    val activity = getActivity.asInstanceOf[EnsichatActivity]
    activity.runOnServiceConnected(() => {
      chatService = activity.service

      database.getContact(address).foreach(c => getActivity.setTitle(c.name))

      adapter = new DatesAdapter(getActivity,
        new MessagesAdapter(getActivity, database.getMessagesCursor(address, None), address))

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
      address = new Address(savedInstanceState.getByteArray("address"))

    LocalBroadcastManager.getInstance(getActivity)
      .registerReceiver(onMessageReceivedReceiver, new IntentFilter(ChatService.ActionMessageReceived))
  }

  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    outState.putByteArray("address", address.bytes)
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    LocalBroadcastManager.getInstance(getActivity).unregisterReceiver(onMessageReceivedReceiver)
  }

  /**
   * Send message if send button was clicked.
   */
  override def onClick(view: View): Unit = view.getId match {
    case R.id.send =>
      val text = messageText.getText.toString.trim
      if (!text.isEmpty) {
        val message = new Text(text.toString)
        val success = chatService.sendTo(address, message)
        if (success)
          messageText.getText.clear()
      }
  }

  /**
   * Displays new messages in UI.
   */
  private val onMessageReceivedReceiver = new BroadcastReceiver {
    override def onReceive(context: Context, intent: Intent): Unit = {
      val msg = intent.getSerializableExtra(ChatService.ExtraMessage).asInstanceOf[Message]
      if (!Set(msg.header.origin, msg.header.target).contains(address))
        return

      msg.body match {
        case _: Text =>
          adapter.changeCursor(database.getMessagesCursor(address, None))
        case _ =>
      }
    }
  }

}
