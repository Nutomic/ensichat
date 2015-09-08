package com.nutomic.ensichat.core.interfaces

object Log {

  def setLogClass[T](logClass: Class[T]) = {
    this.logClass = Option(logClass)
  }

  private var logClass: Option[Class[_]] = None

  def v(tag: String, message: String, tr: Throwable = null) = log("v", tag, message, tr)

  def d(tag: String, message: String, tr: Throwable = null) = log("d", tag, message, tr)

  def i(tag: String, message: String, tr: Throwable = null) = log("i", tag, message, tr)

  def w(tag: String, message: String, tr: Throwable = null) = log("w", tag, message, tr)

  def e(tag: String, message: String, tr: Throwable = null) = log("e", tag, message, tr)

  private def log(level: String, tag: String, message: String, throwable: Throwable) = logClass match {
    case Some(l) =>
      l.getMethod(level, classOf[String], classOf[String], classOf[Throwable])
        .invoke(null, tag, message, throwable)
    case None =>
      System.out.println(level + tag + message + throwable)
  }

}
