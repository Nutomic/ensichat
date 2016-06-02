package com.nutomic.ensichat.core.util

import java.util.{TimerTask, Timer}

import com.nutomic.ensichat.core.{Address, Message}
import org.joda.time.{DateTime, Duration}

/**
  * Contains messages that couldn't be forwarded because we don't know a route.
  */
class MessageBuffer(retryMessageSending: (Address) => Unit) {

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
    val newEntry = new BufferEntry(msg, DateTime.now, 0)
    values += newEntry
    retryMessage(newEntry)
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

  private def handleTimeouts(): Unit = {
    values = values.filter { e =>
      e.retryCount < MaxRetryCount
    }
  }

}
