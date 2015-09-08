package com.nutomic.ensichat.core

import com.nutomic.ensichat.core.header.ContentHeader
import com.nutomic.ensichat.core.interfaces.Settings

/**
 * Generates sequence numbers according to protocol, which are stored persistently.
 */
class SeqNumGenerator(preferences: Settings) {

  private val KeySequenceNumber = "sequence_number"

  private var current = preferences.get(KeySequenceNumber, ContentHeader.SeqNumRange.head)

  def next(): Int = {
    current += 1
    preferences.put(KeySequenceNumber, current)
    current
  }

}
