package com.nutomic.ensichat.core.util

import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext, Future}

/**
 * Wraps [[Future]], so that exceptions are always thrown.
 *
 * @see https://github.com/saturday06/gradle-android-scala-plugin/issues/56
 */
object FutureHelper {

  private val logger = Logger(this.getClass)

  def apply[A](action: => A)(implicit executor: ExecutionContext): Future[A] = {
    val f = Future(action)
    f.onFailure {
      case e =>
        // HACK: Android does not close app when crash occurs in background thread, and there's no
        //       cross-platform way to execute on the foreground thread.
        //       We use this to make sure exceptions are not hidden in the logs.
        logger.error("Exception in Future", e)
        //System.exit(-1)
    }
    f
  }

}
