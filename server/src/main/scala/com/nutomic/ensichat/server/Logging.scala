package com.nutomic.ensichat.server

import java.io.{PrintWriter, StringWriter}
import java.text.DateFormat
import java.util.{Date, Locale}

import com.nutomic.ensichat.core.interfaces.Log

import scala.collection.mutable

class Logging extends Log {

  private val logs = new mutable.Queue[String]()

  def dequeue(): Seq[String] = logs.dequeueAll((String) => true)

  private def enqueue(tag: String, message: String, tr: Option[Throwable]): Unit = {
    val df = DateFormat.getTimeInstance(DateFormat.DEFAULT, Locale.UK)
    val throwableString = tr.map { tr =>
      val sw = new StringWriter()
      tr.printStackTrace(new PrintWriter(sw))
      "\n" + sw.toString
    }
    logs.enqueue(df.format(new Date()) + " " + tag + ": " + message + throwableString.getOrElse(""))
  }

  def v(tag: String, message: String, tr: Throwable = null): Unit =
    enqueue("V/" + tag, message, Option(tr))

  def d(tag: String, message: String, tr: Throwable = null): Unit =
    enqueue("D/" + tag, message, Option(tr))

  def i(tag: String, message: String, tr: Throwable = null): Unit =
    enqueue("I/" + tag, message, Option(tr))

  def w(tag: String, message: String, tr: Throwable = null): Unit =
    enqueue("W/" + tag, message, Option(tr))

  def e(tag: String, message: String, tr: Throwable = null): Unit =
    enqueue("E/" + tag, message, Option(tr))

}