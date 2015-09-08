package com.nutomic.ensichat.core.interfaces

import com.nutomic.ensichat.core.{Address, Message}

/**
 * Transfers data to another node over a certain medium (eg Internet or Bluetooth).
 */
trait TransmissionInterface {

  def create(): Unit

  def destroy(): Unit

  def send(nextHop: Address, msg: Message): Unit

  def getConnections: Set[Address]

}
