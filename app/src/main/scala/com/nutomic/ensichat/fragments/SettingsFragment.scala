package com.nutomic.ensichat.fragments

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.{Preference, PreferenceFragment, PreferenceManager}
import com.nutomic.ensichat.activities.EnsichatActivity
import com.nutomic.ensichat.fragments.SettingsFragment._
import com.nutomic.ensichat.protocol.body.UserInfo
import com.nutomic.ensichat.util.Database
import com.nutomic.ensichat.{BuildConfig, R}

object SettingsFragment {

  val KeyUserName     = "user_name"
  val KeyUserStatus   = "user_status"
  val KeyScanInterval = "scan_interval_seconds"
  val MaxConnections  = "max_connections"
  val Version         = "version"

}

/**
 * Settings screen.
 */
class SettingsFragment extends PreferenceFragment with OnPreferenceChangeListener
  with OnSharedPreferenceChangeListener {

  private lazy val database = new Database(getActivity)

  private lazy val name           = findPreference(KeyUserName)
  private lazy val status         = findPreference(KeyUserStatus)
  private lazy val scanInterval   = findPreference(KeyScanInterval)
  private lazy val maxConnections = findPreference(MaxConnections)
  private lazy val version        = findPreference(Version)

  private lazy val prefs = PreferenceManager.getDefaultSharedPreferences(getActivity)

  override def onCreate(savedInstanceState: Bundle): Unit =  {
    super.onCreate(savedInstanceState)

    addPreferencesFromResource(R.xml.settings)

    name.setSummary(prefs.getString(KeyUserName, ""))
    name.setOnPreferenceChangeListener(this)
    status.setSummary(prefs.getString(KeyUserStatus, ""))
    status.setOnPreferenceChangeListener(this)

    scanInterval.setOnPreferenceChangeListener(this)
    scanInterval.setSummary(prefs.getString(
      KeyScanInterval, getResources.getString(R.string.default_scan_interval)))

    if (BuildConfig.DEBUG) {
      maxConnections.setOnPreferenceChangeListener(this)
      maxConnections.setSummary(prefs.getString(
        MaxConnections, getResources.getString(R.string.default_max_connections)))
    } else
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
   * Updates summary, sends updated name to contacts.
   */
  override def onPreferenceChange(preference: Preference, newValue: AnyRef): Boolean = {
    preference.setSummary(newValue.toString)
    true
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
    }
  }

}
