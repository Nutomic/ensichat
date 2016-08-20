package com.nutomic.ensichat.core.util

import com.nutomic.ensichat.core.routing.Address

final case class User(address: Address, name: String, status: String)
