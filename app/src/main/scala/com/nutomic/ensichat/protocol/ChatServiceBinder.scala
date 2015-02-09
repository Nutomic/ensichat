package com.nutomic.ensichat.protocol

import android.os.Binder

case class ChatServiceBinder (service: ChatService) extends Binder
