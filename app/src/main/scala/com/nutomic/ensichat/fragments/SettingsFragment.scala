package com.nutomic.ensichat.fragments

import android.os.Bundle
import android.preference.PreferenceFragment
import com.nutomic.ensichat.R

class SettingsFragment extends PreferenceFragment {

  override def onCreate(savedInstanceState: Bundle): Unit =  {
    super.onCreate(savedInstanceState)

    addPreferencesFromResource(R.xml.settings)
  }

}
