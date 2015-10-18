package com.nutomic.ensichat.core.interfaces

object Log {

  def setLogInstance(log: Log) = instance = Option(log)

  private var instance: Option[Log] = None

  def v(tag: String, message: String, tr: Throwable = null) = instance.foreach(_.v(tag, message, tr))
  def d(tag: String, message: String, tr: Throwable = null) = instance.foreach(_.d(tag, message, tr))
  def i(tag: String, message: String, tr: Throwable = null) = instance.foreach(_.i(tag, message, tr))
  def w(tag: String, message: String, tr: Throwable = null) = instance.foreach(_.w(tag, message, tr))
  def e(tag: String, message: String, tr: Throwable = null) = instance.foreach(_.e(tag, message, tr))

}

trait Log {

  def v(tag: String, message: String, tr: Throwable = null)
  def d(tag: String, message: String, tr: Throwable = null)
  def i(tag: String, message: String, tr: Throwable = null)
  def w(tag: String, message: String, tr: Throwable = null)
  def e(tag: String, message: String, tr: Throwable = null)

}
