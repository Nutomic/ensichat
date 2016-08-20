package com.nutomic.ensichat.core.util

import com.nutomic.ensichat.core.interfaces.SettingsInterface
import com.nutomic.ensichat.core.messages.header.ContentHeader

/**
 * Generates sequence numbers according to protocol, which are stored persistently.
 */
final private[core] class SeqNumGenerator(preferences: SettingsInterface) {

  private val KeySequenceNumber = "sequence_number"

  private var current = preferences.get(KeySequenceNumber, ContentHeader.SeqNumRange.head)

  def next(): Int = {
    current += 1
    preferences.put(KeySequenceNumber, current)
    current
  }

}
