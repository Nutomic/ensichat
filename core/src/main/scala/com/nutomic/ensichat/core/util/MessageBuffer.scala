package com.nutomic.ensichat.core.util

import java.util.{TimerTask, Timer}

import com.nutomic.ensichat.core.{Address, Message}
import com.typesafe.scalalogging.Logger
import org.joda.time.{Seconds, DateTime, Duration}

/**
  * Contains messages that couldn't be forwarded because we don't know a route.
  */
class MessageBuffer(localAddress: Address, retryMessageSending: (Address) => Unit) {

  private val logger = Logger(this.getClass)

  /**
    * The maximum number of times we retry to deliver a message.
    */
  private val MaxRetryCount = 6

  private val timer = new Timer()

  private case class BufferEntry(message: Message, added: DateTime, retryCount: Int)

  private var values = Set[BufferEntry]()

  private var isStopped = false

  def stop(): Unit = {
    isStopped = true
    timer.cancel()
  }

  def addMessage(msg: Message): Unit = {
    // For old messages added back from database, find their retry count from send time and offset.
    val retryCount =
      (0 to 6).find { i =>
        msg.header.time.get.plus(calculateNextRetryOffset(i)).isAfter(DateTime.now)
      }
      .getOrElse(6)
    val newEntry = new BufferEntry(msg, DateTime.now, retryCount)
    values += newEntry
    retryMessage(newEntry)
    logger.info(s"Added message to buffer, now ${values.size} messages stored")
  }

  /**
    * Calculates the duration until the next retry, measured from the time the message was added.
    */
  private def calculateNextRetryOffset(retryCount: Int) =
    Duration.standardSeconds(10 ^ (retryCount + 1))

  /**
    * Starts a timer to retry the route discovery.
    *
    * The delivery will not be retried if the [[stop]] was called, the message has timed out from
    * the buffer, the message was sent, or a newer message for the same destination was added.
    */
  private def retryMessage(entry: BufferEntry) {
    timer.schedule(new TimerTask {
      override def run(): Unit = {
        if (isStopped)
          return

        // New entry was added for the same destination, don't retry here any more.
        val newerEntryExists = values
          .filter(_.message.header.target == entry.message.header.target)
          .map(_.added)
          .exists(_.isAfter(entry.added))
        if (newerEntryExists)
          return

        // Don't retry if message was sent in the mean time, or message timed out.
        handleTimeouts()
        if (!values.map(_.message).contains(entry.message))
          return

        retryMessageSending(entry.message.header.target)
        val updated = entry.copy(retryCount = entry.retryCount + 1)
        retryMessage(updated)
      }
    }, calculateNextRetryOffset(entry.retryCount).getMillis)
  }

  /**
    * Returns all buffered messages for destination, and removes them from the buffer.
    */
  def getMessages(destination: Address): Set[Message] = {
    handleTimeouts()
    val ret = values.filter(_.message.header.target == destination)
    values --= ret
    ret.map(_.message)
  }

  def getAllMessages: Set[Message] = values.map(_.message)

  private def handleTimeouts(): Unit = {
    val sizeBefore = values.size
    values = values.filter { e =>
      e.retryCount < MaxRetryCount && e.message.header.origin != localAddress
    }
    val difference = values.size - sizeBefore
    if (difference > 0)
      logger.info(s"Removed $difference message(s), now ${values.size} messages stored")
  }

}
