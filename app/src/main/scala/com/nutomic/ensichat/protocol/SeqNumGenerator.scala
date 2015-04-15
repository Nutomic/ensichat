package com.nutomic.ensichat.protocol

import android.content.Context
import android.preference.PreferenceManager
import com.nutomic.ensichat.protocol.messages.ContentHeader

/**
 * Generates sequence numbers acorrding to protocol, which are stored persistently.
 */
class SeqNumGenerator(context: Context) {

  private val KeySequenceNumber = "sequence_number"

  private val pm = PreferenceManager.getDefaultSharedPreferences(context)

  private var current = pm.getInt(KeySequenceNumber, ContentHeader.SeqNumRange.head)

  def next(): Int = {
    current += 1
    pm.edit().putInt(KeySequenceNumber, current)
    current
  }

}
