package com.nutomic.ensichat

import android.support.multidex.MultiDexApplication
import com.nutomic.ensichat.util.PRNGFixes

class App extends MultiDexApplication {

  override def onCreate(): Unit = {
    super.onCreate()
    PRNGFixes.apply()
  }

}