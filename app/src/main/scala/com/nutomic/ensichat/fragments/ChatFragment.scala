package com.nutomic.ensichat.fragments

import android.app.{Activity, ListFragment}
import android.content.{ActivityNotFoundException, BroadcastReceiver, Context, Intent, IntentFilter}
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.content.LocalBroadcastManager
import android.view.View.OnClickListener
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, ViewGroup}
import android.widget.TextView.OnEditorActionListener
import android.widget._
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.EnsichatActivity
import com.nutomic.ensichat.protocol.body.{InitiatePayment, PaymentInformation, Text}
import com.nutomic.ensichat.protocol.header.ContentHeader
import com.nutomic.ensichat.protocol.{Address, ChatService, Message}
import com.nutomic.ensichat.util.Database
import com.nutomic.ensichat.views.{DatesAdapter, MessagesAdapter}
import de.schildbach.wallet.integration.android.BitcoinIntegration

/**
 * Represents a single chat with another specific device.
 */
class ChatFragment extends ListFragment with OnClickListener with OnEditorActionListener {

  private val REQUEST_FETCH_PAYMENT_REQUEST = 1

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

  private var sendBitcoinButton: ImageButton = _

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

      // TODO: mark messages read

      if (listView != null) {
        listView.setAdapter(adapter)
      }
    })
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
        savedInstanceState: Bundle): View = {
    val view          = inflater.inflate(R.layout.fragment_chat, container, false)
    sendBitcoinButton = view.findViewById(R.id.send_bitcoin).asInstanceOf[ImageButton]
    sendButton        = view.findViewById(R.id.send).asInstanceOf[Button]
    messageText       = view.findViewById(R.id.message).asInstanceOf[EditText]
    listView          = view.findViewById(android.R.id.list).asInstanceOf[ListView]

    sendBitcoinButton.setOnClickListener(this)
    sendButton.setOnClickListener(this)
    messageText.setOnEditorActionListener(this)
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

  override def onEditorAction(view: TextView, actionId: Int, event: KeyEvent): Boolean = {
    if (actionId == EditorInfo.IME_ACTION_DONE) {
      onClick(sendButton)
      true
    } else
      false
  }

  /**
   * Send message if send button was clicked.
   */
  override def onClick(view: View): Unit = view.getId match {
    case R.id.send_bitcoin =>
      chatService.sendTo(address, new InitiatePayment())
    case R.id.send =>
      val text = messageText.getText.toString.trim
      if (!text.isEmpty) {
        val message = new Text(text)
        chatService.sendTo(address, message)
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

      val types: Set[Class[_]] =
        Set(classOf[Text], classOf[InitiatePayment], classOf[PaymentInformation])
      if (!types.contains(msg.body.getClass))
        return

      val header = msg.header.asInstanceOf[ContentHeader]
      if (msg.header.origin != address || header.read)
        return

      database.setMessageRead(header)
      adapter.changeCursor(database.getMessagesCursor(address, None))

      // Special handling for Bitcoin messages.
      // TODO: is this stuff working from background?
      msg.body match {
        case _: Text =>
        case _: InitiatePayment =>
          val pm = PreferenceManager.getDefaultSharedPreferences(getActivity)

          val wallet = pm.getString(SettingsFragment.KeyBitcoinWallet,
            getString(R.string.default_bitcoin_wallet))
          val intent = new Intent()
          intent.setClassName(wallet, "de.schildbach.wallet.ui.FetchPaymentRequestActivity")
          intent.putExtra("sender_name", chatService.getUser(msg.header.origin).name)
          try {
            startActivityForResult(intent, REQUEST_FETCH_PAYMENT_REQUEST)
          } catch {
            case e: ActivityNotFoundException =>
              Toast.makeText(getActivity, R.string.bitcoin_wallet_not_found, Toast.LENGTH_LONG).show();
          }
        case pr: PaymentInformation =>
          BitcoinIntegration.request(getActivity, pr.bytes)
      }
    }
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = requestCode match {
    case REQUEST_FETCH_PAYMENT_REQUEST =>
      if (resultCode == Activity.RESULT_OK) {
        val pr = new PaymentInformation(data.getByteArrayExtra("payment_request"))
        chatService.sendTo(address, pr)
      }
  }

}
