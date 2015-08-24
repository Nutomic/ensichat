package com.nutomic.ensichat.views

import java.text.DateFormat
import java.util.Date

import android.content.Context
import android.database.Cursor
import android.view._
import android.widget._
import com.mobsandgeeks.adapters.{SimpleSectionAdapter, ViewHandler, InstantCursorAdapter}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.protocol.body.Text
import com.nutomic.ensichat.protocol.header.ContentHeader
import com.nutomic.ensichat.protocol.{Address, Message}
import com.nutomic.ensichat.util.Database

/**
 * Displays [[Message]]s in ListView.
 *
 * We just use the instant adapter for compatibility with [[SimpleSectionAdapter]], but don't use
 * the annotations (as it breaks separation of presentation and content).
 */
class MessagesAdapter(context: Context, cursor: Cursor, remoteAddress: Address) extends
  InstantCursorAdapter[Message](context, R.layout.item_message, classOf[Message], cursor) {

  /**
   * Free space to the right/left to a message depending on who sent it, in dip.
   */
  private val MessageMargin = 50

  setViewHandler(R.id.root, new ViewHandler[Message] {
    override def handleView(adapter: ListAdapter, parent: View, view: View, msg: Message,
                            position: Int): Unit = {
      val root = view.asInstanceOf[LinearLayout]
      val container = view.findViewById(R.id.container).asInstanceOf[LinearLayout]
      val text = view.findViewById(R.id.text).asInstanceOf[TextView]
      val time = view.findViewById(R.id.time).asInstanceOf[TextView]

      text.setText(msg.body.asInstanceOf[Text].text)
      val formattedDate = DateFormat
        .getTimeInstance(DateFormat.SHORT)
        .format(msg.header.time.get)
      time.setText(formattedDate)

      val lp = new LinearLayout.LayoutParams(view.getLayoutParams)
      val margin = (MessageMargin * context.getResources.getDisplayMetrics.density).toInt
      if (msg.header.origin != remoteAddress) {
        container.setGravity(Gravity.RIGHT)
        root.setGravity(Gravity.RIGHT)
        lp.setMargins(margin, 0, 0, 0)
      } else {
        container.setGravity(Gravity.LEFT)
        root.setGravity(Gravity.LEFT)
        lp.setMargins(0, 0, margin, 0)
      }
      view.setLayoutParams(lp)

      view
    }
  })

  override def getInstance (cursor: Cursor) = Database.messageFromCursor(cursor)

}
