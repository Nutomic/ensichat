package com.nutomic.ensichat.fragments

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.{Preference, PreferenceFragment, PreferenceManager}
import com.nutomic.ensichat.{BuildConfig, R}
import com.nutomic.ensichat.activities.EnsichatActivity
import com.nutomic.ensichat.core.body.UserInfo
import com.nutomic.ensichat.core.interfaces.Settings._
import com.nutomic.ensichat.fragments.SettingsFragment._
import com.nutomic.ensichat.util.Database

object SettingsFragment {
  val Version = "version"
}

/**
 * Settings screen.
 */
class SettingsFragment extends PreferenceFragment with OnSharedPreferenceChangeListener {

  private lazy val database = new Database(getActivity)

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
        val service = getActivity.asInstanceOf[EnsichatActivity].service
        val ui = new UserInfo(prefs.getString(KeyUserName, ""), prefs.getString(KeyUserStatus, ""))
        database.getContacts.foreach(c => service.get.sendTo(c.address, ui))
      case _ =>
    }
  }

}
