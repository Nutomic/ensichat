package com.nutomic.ensichat.core.interfaces

object SettingsInterface {

  val KeyUserName             = "user_name"
  val KeyUserStatus           = "user_status"
  val KeyNotificationSoundsOn = "notification_sounds"

  /**
   * NOTE: Stored as string.
   */
  val KeyScanInterval         = "scan_interval_seconds"

  /**
   * NOTE: Stored as string.
   */
  val KeyMaxConnections       = "max_connections"

  /**
   * NOTE: Stored as comma separated string.
   */
  val KeyServers              = "servers"

  val DefaultUserStatus           = "Let's chat!"
  val DefaultScanInterval         = 15
  val DefaultNotificationSoundsOn = true
  val DefaultMaxConnections       = 1000000
  val DefaultServers              = Set("46.101.249.188:26344", "46.101.238.107:26344")

}

/**
 * Interface for persistent storage of key value pairs.
 *
 * Must support at least storage of String, Int, Long.
 */
trait SettingsInterface {

  def put[T](key: String, value: T): Unit
  def get[T](key: String, default: T): T

}
