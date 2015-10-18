package com.nutomic.ensichat

import android.support.multidex.MultiDexApplication
import com.nutomic.ensichat.core.interfaces.Log
import com.nutomic.ensichat.util.{Logging, PRNGFixes}

class App extends MultiDexApplication {

  override def onCreate(): Unit = {
    super.onCreate()
    Log.setLogInstance(new Logging())
    PRNGFixes.apply()
  }

}