package com.nutomic.ensichat.core.util

import com.nutomic.ensichat.core.body.{RouteReply, RouteRequest}
import com.nutomic.ensichat.core.{Address, Message, Router}
import org.joda.time.{DateTime, Duration}

/**
  * Contains information about AODVv2 control messages that have been received.
  *
  * This class handles Route Request and Route Reply messages (referred to as "route messages").
  *
  * See AODVv2-13 4.6 (Multicast Route Message Table),                                        -> implemented
  *               6.8 (Surpressing Redundant Messages Using the Multicast Route Message Table) -> implemented  (hopefully correct)
  */
private[core] class RouteMessageInfo {

  private val MaxSeqnumLifetime = Duration.standardSeconds(300)

  /**
    * @param messageType Either [[RouteRequest.Type]] or [[RouteReply.Type]].
    * @param origAddress Source address of the route message triggering the route request.
    * @param targAddress Destination address of the route message triggering the route request.
    * @param origSeqNum Sequence number associated with the route to [[origAddress]], if route
    *                   message is an RREQ.
    * @param targSeqNum Sequence number associated with the route to [[targAddress]], if present in
    *                   the route message.
    * @param metric Metric value received in the route message.
    * @param timestamp Last time this entry was updated.
    */
  private case class RouteMessageEntry(messageType: Int, origAddress: Address,
                                       targAddress: Address, origSeqNum: Int, targSeqNum: Int,
                                       metric: Int, timestamp: DateTime)

  private var entries = Set[RouteMessageEntry]()

  private def addEntry(msg: Message): Unit = msg.body match {
    case rreq: RouteRequest =>
      entries += new RouteMessageEntry(RouteRequest.Type, msg.header.origin, msg.header.target,
                                       msg.header.seqNum, rreq.targSeqNum, rreq.originMetric,
                                       DateTime.now)
    case rrep: RouteReply =>
      entries += new RouteMessageEntry(RouteReply.Type, msg.header.origin, msg.header.target,
                                       msg.header.seqNum, rrep.originSeqNum, rrep.originMetric,
                                       DateTime.now)
  }

  def isMessageRedundant(msg: Message): Boolean = {
    handleTimeouts()
    val existingEntry =
      entries.find { e =>
        val haveEntry = e.messageType == msg.header.protocolType &&
          e.origAddress == msg.header.origin && e.targAddress == msg.header.target

        val (metric, seqNumComparison) = msg.body match {
          case rreq: RouteRequest => (rreq.originMetric, Router.compare(rreq.originSeqNum, e.origSeqNum))
          case rrep: RouteReply => (rrep.originMetric, Router.compare(rrep.originSeqNum, e.targSeqNum))
        }
        val isMetricBetter = e.metric < metric
        haveEntry && (seqNumComparison > 0 || (seqNumComparison == 0 && isMetricBetter))
      }
    if (existingEntry.isDefined)
        entries = entries - existingEntry.get

    addEntry(msg)

    existingEntry.isDefined
  }

  private def handleTimeouts(): Unit = {
    entries = entries.filter { e =>
      DateTime.now.isBefore(e.timestamp.plus(MaxSeqnumLifetime))
    }
  }
}