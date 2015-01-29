package com.nutomic.ensichat.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.{PreferenceManager, Preference, PreferenceFragment}
import android.util.Log
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.EnsiChatActivity
import com.nutomic.ensichat.protocol.Address
import com.nutomic.ensichat.protocol.messages.UserName
import com.nutomic.ensichat.fragments.SettingsFragment._

object SettingsFragment {

  val KeyUserName = "user_name"
  
  val KeyScanInterval = "scan_interval_seconds"
  
}

/**
 * Settings screen.
 */
class SettingsFragment extends PreferenceFragment with OnPreferenceChangeListener {

  override def onCreate(savedInstanceState: Bundle): Unit =  {
    super.onCreate(savedInstanceState)

    addPreferencesFromResource(R.xml.settings)
    val name = findPreference(KeyUserName)
    name.setOnPreferenceChangeListener(this)
    val scanInterval = findPreference(KeyScanInterval)
    scanInterval.setOnPreferenceChangeListener(this)
    
    val pm = PreferenceManager.getDefaultSharedPreferences(getActivity)
    name.setSummary(pm.getString(KeyUserName, ""))
    scanInterval.setSummary(pm.getString(KeyScanInterval, "15"))
  }

  /**
   * Updates summary, sends updated name to contacts.
   */
  override def onPreferenceChange(preference: Preference, newValue: AnyRef): Boolean = {
    if (preference.getKey == KeyUserName) {
      val service = getActivity.asInstanceOf[EnsiChatActivity].service
      service.Database.getContacts
        .foreach(c => service.sendTo(c.Address, new UserName(newValue.toString)))
    }
    preference.setSummary(newValue.toString)
    true
  }

}
