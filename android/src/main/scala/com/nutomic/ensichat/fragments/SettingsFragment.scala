package com.nutomic.ensichat.fragments

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.{Intent, SharedPreferences}
import android.os.Bundle
import android.preference.{PreferenceFragment, PreferenceManager}
import com.nutomic.ensichat.activities.EnsichatActivity
import com.nutomic.ensichat.core.messages.body.UserInfo
import com.nutomic.ensichat.core.interfaces.SettingsInterface._
import com.nutomic.ensichat.fragments.SettingsFragment._
import com.nutomic.ensichat.service.ChatService
import com.nutomic.ensichat.{BuildConfig, R}

object SettingsFragment {
  val Version = "version"
}

/**
 * Settings screen.
 */
class SettingsFragment extends PreferenceFragment with OnSharedPreferenceChangeListener {

  private lazy val activity = getActivity.asInstanceOf[EnsichatActivity]

  private lazy val maxConnections       = findPreference(KeyMaxConnections)
  private lazy val version              = findPreference(Version)

  private lazy val prefs = PreferenceManager.getDefaultSharedPreferences(getActivity)

  override def onCreate(savedInstanceState: Bundle): Unit =  {
    super.onCreate(savedInstanceState)

    addPreferencesFromResource(R.xml.settings)

    if (!BuildConfig.DEBUG)
      getPreferenceScreen.removePreference(maxConnections)

    val packageInfo = getActivity.getPackageManager.getPackageInfo(getActivity.getPackageName, 0)
    version.setSummary(packageInfo.versionName)
    prefs.registerOnSharedPreferenceChangeListener(this)
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    prefs.unregisterOnSharedPreferenceChangeListener(this)
  }

  /**
   * Sends the updated username or status to all contacts.
   */
  override def onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
    key match {
      case KeyUserName | KeyUserStatus =>
        val ui = new UserInfo(prefs.getString(KeyUserName, ""), prefs.getString(KeyUserStatus, ""))
        activity.database.get.getContacts.foreach(c =>  activity.service.get.sendTo(c.address, ui))
      case KeyAddresses =>
        val intent = new Intent(getActivity, classOf[ChatService])
        intent.setAction(ChatService.ActionNetworkChanged)
        getActivity.startService(intent)
      case _ =>
    }
  }

}
