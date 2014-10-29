package com.nutomic.ensichat.bluetooth

import android.os.Binder

class ChatServiceBinder (service: ChatService) extends Binder {

  def getService = service

}
