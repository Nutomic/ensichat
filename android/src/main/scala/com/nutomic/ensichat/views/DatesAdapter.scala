package com.nutomic.ensichat.views

import java.text.DateFormat

import android.content.Context
import com.mobsandgeeks.adapters.{Sectionizer, SimpleSectionAdapter}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.core.Message

import scala.collection.JavaConverters._

object DatesAdapter {

  private val Sectionizer = new Sectionizer[Message]() {
    override def getSectionTitleForItem(item: Message): String = {
      DateFormat
        .getDateInstance(DateFormat.MEDIUM)
        .format(item.header.time.get.toDate)
    }
  }

}

/**
 * Wraps [[MessagesAdapter]] and shows date between messages.
 */
class DatesAdapter(context: Context, messagesAdapter: MessagesAdapter)
  extends SimpleSectionAdapter[Message](context, messagesAdapter, R.layout.item_date, R.id.date,
    DatesAdapter.Sectionizer) {

  def replaceItems(items: Seq[Message]): Unit = {
    messagesAdapter.clear()
    messagesAdapter.addAll(items.asJava)
    notifyDataSetChanged()
  }

}
