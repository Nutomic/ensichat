package com.nutomic.ensichat.protocol

import android.os.Binder

class ChatServiceBinder (service: ChatService) extends Binder {

  def getService = service

}
