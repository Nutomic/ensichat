package com.nutomic.ensichat.views

import java.text.DateFormat
import java.util.Date

import android.content.Context
import android.database.Cursor
import com.mobsandgeeks.adapters.{Sectionizer, SimpleSectionAdapter}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.protocol.Message
import com.nutomic.ensichat.protocol.header.ContentHeader
import com.nutomic.ensichat.util.Database

object DatesAdapter {

  private val Sectionizer = new Sectionizer[Cursor]() {
    override def getSectionTitleForItem(item: Cursor): String = {
      DateFormat
        .getDateInstance(DateFormat.MEDIUM)
        .format(Database.textMessageFromCursor(item).header.time.get)
    }
  }

}

/**
 * Wraps [[MessagesAdapter]] and shows date between messages.
 */
class DatesAdapter(context: Context, messagesAdapter: MessagesAdapter)
  extends SimpleSectionAdapter[Cursor](context, messagesAdapter, R.layout.item_date, R.id.date,
    DatesAdapter.Sectionizer) {

  def changeCursor(cursor: Cursor): Unit = {
    messagesAdapter.changeCursor(cursor)
    notifyDataSetChanged()
  }

}
