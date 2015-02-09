package com.nutomic.ensichat.protocol

import com.nutomic.ensichat.protocol.messages.{Message, MessageHeader}

/**
 * Forwards messages to all connected devices.
 */
class Router(activeConnections: () => Set[Address], send: (Address, Message) => Unit) {

  private var messageSeen = Set[(Address, Int)]()

  def onReceive(msg: Message): Unit = {
    val info = (msg.Header.Origin, msg.Header.SeqNum)
    if (messageSeen.contains(info))
      return

    activeConnections().foreach(a => send(a, msg))
    
    trimMessageSeen(info._1, info._2)
    messageSeen += info
  }

  /**
   * Removes old entries from [[messageSeen]].
   *
   * Only the last half of possible sequence number values are kept. For example, if sequence
   * numbers are between 0 and 10, and a new message with sequence number 6 arrives, all entries
   * for messages with sequence numbers outside [2, 6] are removed.
   */
  private def trimMessageSeen(a1: Address, s1: Int): Unit = {
    messageSeen = messageSeen.filter { case (a2, s2) =>
      if (a1 != a2)
        true

      // True if [[s2]] is between {{{MessageHeader.SeqNumRange.size / 2}}} and
      // [[MessageHeader.SeqNumRange.size]].
      if (s1 > MessageHeader.SeqNumRange.size / 2) {
        // True if [[s2]] is between {{{s1 - MessageHeader.SeqNumRange.size / 2}}} and [[s1]].
        s1 - MessageHeader.SeqNumRange.size / 2 < s2 && s2 < s1
      } else {
        // True if [[s2]] is *not* between [[s1]] and {{{s1 + MessageHeader.SeqNumRange.size / 2}}}.
        s2 < s1 || s2 > s1 + MessageHeader.SeqNumRange.size / 2
      }
    }
  }

}