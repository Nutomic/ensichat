package com.nutomic.ensichat.bluetooth

import android.os.Binder

class ChatServiceBinder (mService: ChatService) extends Binder {

  def getService(): ChatService = {
    return mService
  }

}
