package com.nutomic.ensichat.protocol

import com.nutomic.ensichat.protocol.header.{ContentHeader, MessageHeader}

/**
 * Forwards messages to all connected devices.
 */
class Router(activeConnections: () => Set[Address], send: (Address, Message) => Unit) {

  private var messageSeen = Set[(Address, Int)]()

  def onReceive(msg: Message): Unit = {
    val info = (msg.header.origin, msg.header.seqNum)
    if (messageSeen.contains(info))
      return

    val updated = incHopCount(msg)
    if (updated.header.hopCount >= updated.header.hopLimit)
      return

    activeConnections().foreach(a => send(a, updated))
    
    trimMessageSeen(info._1, info._2)
    messageSeen += info
  }

  /**
   * Returns msg with hop count increased by one.
   */
  private def incHopCount(msg: Message): Message = {
    val updatedHeader = msg.header match {
      case ch: ContentHeader => new ContentHeader(ch.origin, ch.target, ch.seqNum, ch.contentType,
                                    ch.messageId, ch.time, ch.read, ch.hopCount + 1, ch.hopLimit)
      case mh: MessageHeader => new MessageHeader(mh.protocolType, mh.origin, mh.target, mh.seqNum,
                                    mh.hopCount + 1, mh.hopLimit)
    }
    new Message(updatedHeader, msg.crypto, msg.body)
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
      if (s1 > ContentHeader.SeqNumRange.size / 2) {
        // True if [[s2]] is between {{{s1 - MessageHeader.SeqNumRange.size / 2}}} and [[s1]].
        s1 - ContentHeader.SeqNumRange.size / 2 < s2 && s2 < s1
      } else {
        // True if [[s2]] is *not* between [[s1]] and {{{s1 + MessageHeader.SeqNumRange.size / 2}}}.
        s2 < s1 || s2 > s1 + ContentHeader.SeqNumRange.size / 2
      }
    }
  }

}
