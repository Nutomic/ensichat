package com.nutomic.ensichat.core.util

import scala.concurrent.{ExecutionContext, Future}

/**
 * Wraps [[Future]], so that exceptions are always thrown.
 *
 * @see https://github.com/saturday06/gradle-android-scala-plugin/issues/56
 */
object FutureHelper {

  def apply[A](action: => A)(implicit executor: ExecutionContext): Future[A] = {
    val f = Future(action)
    f.onFailure {
      case e =>
        throw e
    }
    f
  }

}
