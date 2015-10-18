package com.nutomic.ensichat.util

import android.util
import com.nutomic.ensichat.core.interfaces.Log

class Logging extends Log {
  
  def v(tag: String, message: String, tr: Throwable = null) = util.Log.v(tag, message, tr)
  def d(tag: String, message: String, tr: Throwable = null) = util.Log.d(tag, message, tr)
  def i(tag: String, message: String, tr: Throwable = null) = util.Log.i(tag, message, tr)
  def w(tag: String, message: String, tr: Throwable = null) = util.Log.w(tag, message, tr)
  def e(tag: String, message: String, tr: Throwable = null) = util.Log.e(tag, message, tr)
}