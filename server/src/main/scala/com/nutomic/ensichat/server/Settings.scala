package com.nutomic.ensichat.server

import java.io._
import java.util.Properties

import com.nutomic.ensichat.core.interfaces.SettingsInterface
import com.typesafe.scalalogging.Logger

import scala.collection.JavaConverters._

class Settings(file: File) extends SettingsInterface {

  private val logger = Logger(this.getClass)

  if (!file.exists()) {
    file.createNewFile()
    put(SettingsInterface.KeyUserName, "unknown user")
  }

  private lazy val props: Properties = {
    val p = new Properties()
    try {
      val fis = new InputStreamReader(new FileInputStream(file), "UTF-8")
      p.load(fis)
      fis.close()
    } catch {
      case e: IOException => logger.warn("Failed to load settings from " + file, e)
    }
    p
  }

  def put[T](key: String, value: T): Unit = {
    props.asScala.put(key, value.toString)
    try {
      val fos = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")
      props.store(fos, "")
      fos.close()
    } catch {
      case e: IOException => logger.warn("Failed to write preference for key " + key, e)
    }
  }

  def get[T](key: String, default: T): T = {
    val value = props.asScala.getOrElse[String](key, default.toString)
    val cast = default match {
      case _: Int    => value.toInt
      case _: Long   => value.toLong
      case _: String => value
    }
    // This has no effect due to type erasure, but is needed to avoid compiler error.
    cast.asInstanceOf[T]
  }

}