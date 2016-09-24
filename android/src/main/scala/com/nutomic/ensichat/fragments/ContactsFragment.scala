package com.nutomic.ensichat.fragments

import java.io.File

import android.app.ListFragment
import android.bluetooth.BluetoothAdapter
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.FloatingActionButton
import android.support.v4.content.{ContextCompat, LocalBroadcastManager}
import android.support.v7.widget.Toolbar
import android.view.View.OnClickListener
import android.view._
import android.widget.{ListView, TextView}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.{ConnectionsActivity, EnsichatActivity, MainActivity, SettingsActivity}
import com.nutomic.ensichat.core.interfaces.SettingsInterface
import com.nutomic.ensichat.service.{CallbackHandler, ChatService}
import com.nutomic.ensichat.views.UsersAdapter

import scala.collection.JavaConversions._

/**
 * Lists all nearby, connected devices.
 */
class ContactsFragment extends ListFragment with OnClickListener {

  private lazy val adapter = new UsersAdapter(getActivity)

  private lazy val database = activity.database.get

  private lazy val lbm = LocalBroadcastManager.getInstance(getActivity)

  private lazy val activity = getActivity.asInstanceOf[EnsichatActivity]

  private var title: TextView = _
  private var subtitle: TextView = _

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    setListAdapter(adapter)
    setHasOptionsMenu(true)
    lbm.registerReceiver(onContactsUpdatedListener, new IntentFilter(CallbackHandler.ActionContactsUpdated))
    lbm.registerReceiver(onConnectionsChangedListener, new IntentFilter(CallbackHandler.ActionConnectionsChanged))
  }

  override def onResume(): Unit = {
    super.onResume()
    activity.runOnServiceConnected(() => {
      adapter.clear()
      adapter.addAll(database.getContacts)
      updateConnections()
    })
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    lbm.unregisterReceiver(onContactsUpdatedListener)
    lbm.unregisterReceiver(onConnectionsChangedListener)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
                            savedInstanceState: Bundle): View = {
    val v = inflater.inflate(R.layout.fragment_contacts, container, false)
    val toolbar = v.findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    v.findViewById(R.id.title_holder).setOnClickListener(this)
    activity.setSupportActionBar(toolbar)
    toolbar.setNavigationIcon(R.drawable.ic_launcher)
    title = v.findViewById(R.id.title).asInstanceOf[TextView]
    subtitle = v.findViewById(R.id.subtitle).asInstanceOf[TextView]
    val fab = v.findViewById(R.id.fab).asInstanceOf[FloatingActionButton]
    fab.setOnClickListener(this)
    updateConnections()
    v
  }

  override def onCreateOptionsMenu(menu: Menu, inflater: MenuInflater): Unit = {
    super.onCreateOptionsMenu(menu, inflater)
    inflater.inflate(R.menu.main, menu)
  }

  override def onClick(v: View): Unit =
    startActivity(new Intent(getActivity, classOf[ConnectionsActivity]))

  override def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {
    case R.id.share_app =>
      val pm = getActivity.getPackageManager
      val ai = pm.getInstalledApplications(0).find(_.sourceDir.contains(getActivity.getPackageName))
      val intent = new Intent()
      intent.setAction(Intent.ACTION_SEND)
      intent.setType("*/*")
      intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(ai.get.sourceDir)))
      startActivity(intent)
      true
    case R.id.my_address =>
      val prefs = PreferenceManager.getDefaultSharedPreferences(getActivity)
      val fragment = new UserInfoFragment()
      val bundle = new Bundle()
      bundle.putString(
        UserInfoFragment.ExtraAddress, ChatService.newCrypto(getActivity).localAddress.toString)
      bundle.putString(
        UserInfoFragment.ExtraUserName, prefs.getString(SettingsInterface.KeyUserName, ""))
      bundle.putBoolean(UserInfoFragment.ExtraShowQr, true)
      fragment.setArguments(bundle)
      fragment.show(getFragmentManager, "dialog")
      true
    case R.id.settings =>
      startActivity(new Intent(getActivity, classOf[SettingsActivity]))
      true
    case _ =>
      super.onOptionsItemSelected(item)
  }

  /**
   * Opens a chat with the clicked device.
   */
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long): Unit =
    getActivity.asInstanceOf[MainActivity].openChat(adapter.getItem(position).address)

  private val onContactsUpdatedListener = new BroadcastReceiver() {
    override def onReceive(context: Context, intent: Intent): Unit = {
      getActivity.runOnUiThread(new Runnable {
        override def run(): Unit = {
          adapter.clear()
          database.getContacts.foreach(adapter.add)
        }
      })
    }
  }

  private val onConnectionsChangedListener = new BroadcastReceiver {
    override def onReceive(context: Context, intent: Intent): Unit = updateConnections()
  }

  /**
   * Updates TextViews in actionbar with current connections.
   */
  private def updateConnections(): Unit = {
    if (activity.service.isEmpty || title == null)
      return

    val service = activity.service.get
    val connections = service.connections()
    val count = connections.size
    val color = count match {
      case 0 => R.color.title_connections_error
      case 1 => R.color.title_connections_warning
      case _ => R.color.title_connections_ok
    }

    title.setText(getResources.getQuantityString(R.plurals.title_connections, count, count.toString))
    title.setTextColor(ContextCompat.getColor(getActivity, color))

    subtitle.setText(connections.map(service.getUser(_).name).mkString(", "))
    subtitle.setVisibility(if (count == 0) View.GONE else View.VISIBLE)
  }

}
