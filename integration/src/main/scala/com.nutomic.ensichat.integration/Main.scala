package com.nutomic.ensichat.integration

import java.io.File
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.{Timer, TimerTask}
import com.nutomic.ensichat.core.messages.body.Text
import com.nutomic.ensichat.core.util.Crypto

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try
import scalax.file.Path

/**
 * Creates some local nodes, connects them and sends messages between them.
 *
 * If the test runs slow or fails, changing [[Crypto.PublicKeySize]] to 512 should help.
 *
 * These tests are somewhat fragile, and might fail randomly. It helps to run only one of
 * the test functions at a time.
 */
object Main extends App {

  testNeighborSending()
  testMeshMessageSending()
  testIndirectRelay()
  testNeighborRelay()
  testMessageDeliveryOnConnect()
  testSendDelayed()
  testRouteChange()
  testMessageConfirmation()

  private def testNeighborSending(): Unit = {
    val node1 = Await.result(createNode(1), Duration.Inf)
    val node2 = Await.result(createNode(2), Duration.Inf)

    connectNodes(node1, node2)
    sendMessage(node1, node2)

    Set(node1, node2).foreach(_.stop())

    System.out.println("Test neighbor sending successful!")
  }

  private def testNeighborRelay(): Unit = {
    val nodes = createNodes(3)

    connectNodes(nodes(0), nodes(1))

    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = {
        nodes(0).stop()
        connectNodes(nodes(1), nodes(2))
      }
    }, Duration(10, TimeUnit.SECONDS).toMillis)
    sendMessage(nodes(0), nodes(2), 30)

    timer.cancel()
    nodes.foreach(_.stop())

    System.out.println("Test neighbor relay successful!")
  }

  private def testIndirectRelay(): Unit = {
    val nodes = createNodes(5)

    connectNodes(nodes(0), nodes(1))
    connectNodes(nodes(1), nodes(2))
    connectNodes(nodes(2), nodes(3))

    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = {
        nodes(0).stop()
        connectNodes(nodes(3), nodes(4))
      }
    }, Duration(10, TimeUnit.SECONDS).toMillis)
    sendMessage(nodes(0), nodes(4), 30)

    timer.cancel()
    nodes.foreach(_.stop())

    System.out.println("Test indirect sending successful!")
  }

  private def testMeshMessageSending(): Unit = {
    val nodes = createMesh()

    sendMessages(nodes)

    nodes.foreach(_.stop())

    System.out.println("Test mesh message sending successful!")
  }

  /**
    * Stop node 1, forcing route errors and messages to use the (longer) path via nodes 7 and 8.
    */
  private def testRouteChange() {
    val nodes = createMesh()

    sendMessages(nodes)

    nodes(1).stop()
    Thread.sleep(10 * 1000)

    sendMessages(nodes)

    nodes.foreach(_.stop())

    System.out.println("Test route change successful!")
  }

  /**
    * Create new node 9, send message from node 0 to its address, before actually connecting it.
    * The message is automatically delivered when node 9 connects as neighbor.
    */
  private def testMessageDeliveryOnConnect() {
    val nodes = createMesh()
    val node9 = Await.result(createNode(9), Duration.Inf)
    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = {
        connectNodes(nodes(0), node9)
        timer.cancel()
      }
    }, Duration(10, TimeUnit.SECONDS).toMillis)
    sendMessage(nodes(0), node9, 30)

    (nodes :+ node9).foreach(_.stop())

    System.out.println("Test message delivery on connect successful!")
  }

  /**
    * Create new node 10, send message from node 7 to its address, before connecting it to the mesh.
    * The message is delivered after node 7 starts a route discovery triggered by the message buffer.
    */
  private def testSendDelayed(): Unit = {
    val nodes = createMesh()
    val timer = new Timer()
    val node10 = Await.result(createNode(10), Duration.Inf)
    timer.schedule(new TimerTask {
      override def run(): Unit = {
        connectNodes(nodes(0), node10)
        timer.cancel()
      }
    }, Duration(5, TimeUnit.SECONDS).toMillis)
    sendMessage(nodes(7), node10, 30)

    (nodes :+ node10).foreach(_.stop())

    System.out.println("Test send delayed successful!")
  }

  /**
    * Check that message confirmation is sent back after message was received.
    */
  private def testMessageConfirmation(): Unit = {
    val nodes = createNodes(2)

    connectNodes(nodes(0), nodes(1))
    sendMessage(nodes(0), nodes(1))
    assert(nodes(0).database.getMessages(nodes(1).crypto.localAddress).nonEmpty)
    assert(nodes(0).database.getUnconfirmedMessages.isEmpty)

    nodes.foreach(_.stop())
  }

  private def createNodes(count: Int): Seq[LocalNode] = {
    val nodes = Await.result(Future.sequence((0 until count).map(createNode)), Duration.Inf)
    nodes.foreach(n => System.out.println(s"Node ${n.index} has address ${n.crypto.localAddress}"))
    nodes
  }

  /**
    * Creates a new mesh with a predefined layout.
    *
    * Graphical representation:
    *     8 —— 7
    *    /      \
    *   0———1———3———4
    *    \ /    |   |
    *     2     5———6
    *
    * @return List of [[LocalNode]]s, ordered from 0 to 8.
    */
  private def createMesh(): Seq[LocalNode] = {
    val nodes = createNodes(9)

    connectNodes(nodes(0), nodes(1))
    connectNodes(nodes(0), nodes(2))
    connectNodes(nodes(1), nodes(2))
    connectNodes(nodes(1), nodes(3))
    connectNodes(nodes(3), nodes(4))
    connectNodes(nodes(3), nodes(5))
    connectNodes(nodes(4), nodes(6))
    connectNodes(nodes(5), nodes(6))
    connectNodes(nodes(3), nodes(7))
    connectNodes(nodes(0), nodes(8))
    connectNodes(nodes(7), nodes(8))

    nodes
  }

  private def createNode(index: Int): Future[LocalNode] = {
    val configFolder = new File(s"build/node$index/")
    Path(configFolder).deleteRecursively()
    Future(new LocalNode(index, configFolder))
  }

  private def connectNodes(first: LocalNode, second: LocalNode): Unit = {
    first.connectionHandler.connect(s"localhost:${second.port}")

    first.eventQueue.toStream.find(_._1 == LocalNode.EventType.ConnectionsChanged)
    second.eventQueue.toStream.find(_._1 == LocalNode.EventType.ConnectionsChanged)

    val firstAddress      = first.crypto.localAddress
    val secondAddress     = second.crypto.localAddress
    val firstConnections  = first.connectionHandler.connections()
    val secondConnections = second.connectionHandler.connections()

    assert(firstConnections.contains(secondAddress),
      s"${first.index} is not connected to ${second.index}")
    assert(secondConnections.contains(firstAddress),
      s"${second.index} is not connected to ${second.index}")

    System.out.println(s"${first.index} and ${second.index} connected")
  }

  private def sendMessages(nodes: Seq[LocalNode]): Unit = {
    sendMessage(nodes(2), nodes(0))
    sendMessage(nodes(0), nodes(2))
    sendMessage(nodes(4), nodes(3))
    sendMessage(nodes(3), nodes(5))
    sendMessage(nodes(4), nodes(6))
    sendMessage(nodes(2), nodes(3))
    sendMessage(nodes(3), nodes(6))
    sendMessage(nodes(3), nodes(2))
  }


  private def sendMessage(from: LocalNode, to: LocalNode, waitSeconds: Int = 1): Unit = {
    addKey(to.crypto, from.crypto)
    addKey(from.crypto, to.crypto)

    System.out.println(s"sendMessage(${from.index}, ${to.index})")
    val text = s"${from.index} to ${to.index}"
    from.connectionHandler.sendTo(to.crypto.localAddress, new Text(text))

    val latch = new CountDownLatch(1)
    Future {
      val exists =
        to.eventQueue.toStream.exists { event =>
          if (event._1 != LocalNode.EventType.MessageReceived)
            false
          else {
            event._2.get.body match {
              case t: Text => t.text == text
              case _ => false
            }
          }
        }
      assert(exists, s"message from ${from.index} did not arrive at ${to.index}")
      latch.countDown()
    }
    assert(latch.await(waitSeconds, TimeUnit.SECONDS))
  }

  private def addKey(addTo: Crypto, addFrom: Crypto): Unit = {
    if (Try(addTo.getPublicKey(addFrom.localAddress)).isFailure)
      addTo.addPublicKey(addFrom.localAddress, addFrom.getLocalPublicKey)
  }

}