package com.nutomic.ensichat.activities

import android.content.{ComponentName, Context, Intent, ServiceConnection}
import android.os.{Bundle, IBinder}
import android.support.v7.app.AppCompatActivity
import com.nutomic.ensichat.protocol.{ChatService, ChatServiceBinder}

/**
 * Connects to [[ChatService]] and provides access to it.
 */
class EnsichatActivity extends AppCompatActivity with ServiceConnection {

  private var chatService: Option[ChatService] = None

  private var listeners = Set[() => Unit]()

  /**
   * Starts service and connects to it.
   */
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    startService(new Intent(this, classOf[ChatService]))
    bindService(new Intent(this, classOf[ChatService]), this, Context.BIND_AUTO_CREATE)
  }

  /**
   * Unbinds service.
   */
  override def onDestroy(): Unit = {
    super.onDestroy()
    unbindService(this)
  }

  /**
   * Calls all listeners registered with [[runOnServiceConnected]].
   *
   * Clears the list containing them.
   */
  override def onServiceConnected(componentName: ComponentName, iBinder: IBinder): Unit = {
    val binder = iBinder.asInstanceOf[ChatServiceBinder]
    chatService = Option(binder.service)
    listeners.foreach(_())
    listeners = Set.empty
  }

  override def onServiceDisconnected(componentName: ComponentName) =
    chatService = null

  /**
   * Calls l as soon as [[ChatService]] first becomes available.
   */
  def runOnServiceConnected(l: () => Unit): Unit =
    chatService match {
      case Some(s) => l()
      case None => listeners += l
    }

  /**
   * Returns the [[ChatService]].
   *
   * Should only be called after [[runOnServiceConnected]] callback was called.
   */
  def service = chatService.get

}
