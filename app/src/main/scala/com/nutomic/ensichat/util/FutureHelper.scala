package com.nutomic.ensichat.util

import android.os.{Looper, Handler}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Wraps [[Future]], so that exceptions are always thrown.
 *
 * @see https://github.com/saturday06/gradle-android-scala-plugin/issues/56
 */
object FutureHelper {

  def apply[A](action: => A)(implicit executor: ExecutionContext): Future[A] = {
    val handler = new Handler(Looper.getMainLooper)
    val f = Future(action)
    f.onFailure {
      case e =>
        handler.post(new Runnable {
          override def run(): Unit = throw e
        })
    }
    f
  }

}
