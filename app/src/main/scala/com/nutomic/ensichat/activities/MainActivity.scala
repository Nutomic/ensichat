package com.nutomic.ensichat.activities

import android.app.Activity
import android.os.Bundle

import com.nutomic.ensichat.R

class MainActivity extends Activity {

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
  }

}
