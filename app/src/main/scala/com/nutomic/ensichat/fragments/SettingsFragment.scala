package com.nutomic.ensichat.fragments

import android.os.Bundle
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.{Preference, PreferenceFragment, PreferenceManager}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.EnsichatActivity
import com.nutomic.ensichat.fragments.SettingsFragment._
import com.nutomic.ensichat.protocol.body.UserName
import com.nutomic.ensichat.util.Database

object SettingsFragment {

  val KeyUserName = "user_name"
  
  val KeyScanInterval = "scan_interval_seconds"

  val MaxConnections = "max_connections"

  val Version = "version"

}

/**
 * Settings screen.
 */
class SettingsFragment extends PreferenceFragment with OnPreferenceChangeListener {

  private lazy val database = new Database(getActivity)

  override def onCreate(savedInstanceState: Bundle): Unit =  {
    super.onCreate(savedInstanceState)

    addPreferencesFromResource(R.xml.settings)
    val name           = findPreference(KeyUserName)
    val scanInterval   = findPreference(KeyScanInterval)
    val maxConnections = findPreference(MaxConnections)
    val version        = findPreference(Version)
    name.setOnPreferenceChangeListener(this)
    scanInterval.setOnPreferenceChangeListener(this)
    maxConnections.setOnPreferenceChangeListener(this)
    version.setSummary(getActivity.getPackageManager.getPackageInfo(
      getActivity.getPackageName, 0).versionName)
    
    val pm = PreferenceManager.getDefaultSharedPreferences(getActivity)
    name.setSummary(pm.getString(KeyUserName, ""))
    scanInterval.setSummary(pm.getString(KeyScanInterval,
      getResources.getString(R.string.default_scan_interval)))
    maxConnections.setSummary(pm.getString(MaxConnections,
      getResources.getString(R.string.default_max_connections)))
  }

  /**
   * Updates summary, sends updated name to contacts.
   */
  override def onPreferenceChange(preference: Preference, newValue: AnyRef): Boolean = {
    if (preference.getKey == KeyUserName) {
      val service = getActivity.asInstanceOf[EnsichatActivity].service
      database.getContacts.foreach(c => service.sendTo(c.address, new UserName(newValue.toString)))
    }
    preference.setSummary(newValue.toString)
    true
  }

}
