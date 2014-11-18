package com.nutomic.ensichat.activities

import android.app.Fragment
import android.os.Bundle
import android.support.v4.app.NavUtils
import android.view.MenuItem
import com.nutomic.ensichat.fragments.SettingsFragment

/**
 * Holder for [[SettingsFragment]].
 */
class SettingsActivity extends EnsiChatActivity {

  private var fragment: Fragment = _

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    getActionBar.setDisplayHomeAsUpEnabled(true)

    val fm = getFragmentManager
    fragment =
      if (savedInstanceState != null) {
        fm.getFragment(savedInstanceState, "settings_fragment")
      } else {
        new SettingsFragment()
      }
    fm.beginTransaction()
      .replace(android.R.id.content, fragment)
      .commit()
  }

  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)

    getFragmentManager.putFragment(outState, "settings_fragment", fragment)
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {
      case android.R.id.home =>
        NavUtils.navigateUpFromSameTask(this)
        true;
      case _ =>
        super.onOptionsItemSelected(item);
  }

}
