package com.nutomic.ensichat.core

import java.util.Comparator

import com.nutomic.ensichat.core.header.{ContentHeader, MessageHeader}
import com.nutomic.ensichat.core.util.LocalRoutesInfo

object Router extends Comparator[Int] {

  /**
   * Compares which sequence number is newer.
   *
   * @return 1 if lhs is newer, -1 if rhs is newer, 0 if they are equal.
   */
  override def compare(lhs: Int, rhs: Int): Int = {
    if (lhs == rhs)
      0
    // True if [[rhs]] is between {{{MessageHeader.SeqNumRange.size / 2}}} and
    // [[MessageHeader.SeqNumRange.size]].
    else if (lhs > ContentHeader.SeqNumRange.size / 2) {
      // True if [[rhs]] is between {{{lhs - MessageHeader.SeqNumRange.size / 2}}} and [[lhs]].
      if (lhs - ContentHeader.SeqNumRange.size / 2 < rhs && rhs < lhs) 1 else -1
    } else {
      // True if [[rhs]] is *not* between [[lhs]] and {{{lhs + MessageHeader.SeqNumRange.size / 2}}}.
      if (rhs < lhs || rhs > lhs + ContentHeader.SeqNumRange.size / 2) 1 else -1
    }
  }
}

/**
 * Forwards messages to all connected devices.
 */
private[core] class Router(routesInfo: LocalRoutesInfo, send: (Address, Message) => Unit,
                                 noRouteFound: (Message) => Unit) {

  private var messageSeen = Set[(Address, Int)]()

  /**
   * Returns true if we have received the same message before.
   */
  private[core] def isMessageSeen(msg: Message): Boolean = {
    val info = (msg.header.origin, msg.header.seqNum)
    val seen = messageSeen.contains(info)
    markMessageSeen(info)
    seen
  }

  /**
   * Sends message to all connected devices. Should only be called if [[isMessageSeen()]] returns
   * true.
   */
  def forwardMessage(msg: Message, nextHopOption: Option[Address] = None): Unit = {
    if (msg.header.hopCount + 1 >= msg.header.hopLimit)
      return

    val nextHop = nextHopOption.getOrElse(msg.header.target)

    if (nextHop == Address.Broadcast) {
      send(nextHop, msg)
      return
    }

    routesInfo.getRoute(nextHop).map(_.nextHop) match {
      case Some(a) =>
        send(a, incHopCount(msg))
        markMessageSeen((msg.header.origin, msg.header.seqNum))
      case None =>
        noRouteFound(msg)
    }
  }

  private def markMessageSeen(info: (Address, Int)): Unit = {
    trimMessageSeen(info._1, info._2)
    messageSeen += info
  }

  /**
   * Returns msg with hop count increased by one.
   */
  private def incHopCount(msg: Message): Message = {
    val updatedHeader = msg.header match {
      case ch: ContentHeader => new ContentHeader(ch.origin, ch.target, ch.seqNum, ch.contentType,
                                    ch.messageId, ch.time, ch.hopCount + 1, ch.hopLimit)
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

      else
        Router.compare(s1, s2) > 0
    }
  }

}
