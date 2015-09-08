package com.nutomic.ensichat.util

import android.content.Context
import android.preference.PreferenceManager
import com.nutomic.ensichat.core.interfaces.Settings

class SettingsWrapper(context: Context) extends Settings {

  private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

  override def get[T](key: String, default: T): T = default match {
    case s: String => prefs.getString(key, s).asInstanceOf[T]
    case i: Int    => prefs.getInt(key, i).asInstanceOf[T]
    case l: Long   => prefs.getLong(key, l).asInstanceOf[T]
  }

  override def put[T](key: String, value: T): Unit = value match {
    case s: String => prefs.edit().putString(key, s).apply()
    case i: Int    => prefs.edit().putInt(key, i).apply()
    case l: Long   => prefs.edit().putLong(key, l).apply()
  }

}
