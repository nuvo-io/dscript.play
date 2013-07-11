package dscript.runtime

import akka.actor.ActorSystem
import dscript.util.Cache
import org.omg.dds.domain.DomainParticipant
import org.omg.dds.sub.Subscriber
import org.omg.dds.pub.Publisher
import org.omg.dds.topic.Topic
import dscript.entity.{WebWriter, WebReader}
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

object DSRuntime {

  // Initialize the DDS Configuration
  DDSConfig.setup()

  val defaultDomain = 0
  val system = ActorSystem("DDScriptRuntime")
  val pubCacheLock = new java.util.concurrent.locks.ReentrantReadWriteLock()
  val subCacheLock = new java.util.concurrent.locks.ReentrantReadWriteLock()
  val readerCacheLock = new java.util.concurrent.locks.ReentrantReadWriteLock()
  val writerCacheLock = new java.util.concurrent.locks.ReentrantReadWriteLock()


  // Initialize the Participant Cache with the participant for the
  // default domain
  lazy val participantCache = {
    val c = new Cache[Int, DomainParticipant]()
    c.add(defaultDomain, dds.config.DefaultEntities.defaultDomainParticipant)
    c
  }
  lazy val pubCache   = new Cache[(Int, String), (Publisher, Cache[String, WebWriter[_ <: Any]])]
  lazy val subCache   = new Cache[(Int, String), (Subscriber, Cache[String, WebReader[_ <: Any]])]

  lazy val topicCache = new Cache[(Int, String), Topic[_ <: Any]]()

  lazy val readerCache = new Cache[(Int, String), WebReader[_ <: Any]]()
  lazy val writerCache = new Cache[(Int, String), WebWriter[_ <: Any]]()

  lazy val webReaderCache = new Cache[UUID, WebReader[_ <: Any]]()
  lazy val webWriterCache = new Cache[UUID, WebWriter[_ <: Any]]()

  lazy val webReaderRefCount = new Cache[UUID, AtomicInteger]()
  lazy val webWriterRefCount = new Cache[UUID, AtomicInteger]()

  import dscript.synchronizers._


  def leaseWebReader(eid: UUID) = synchronizedRead(readerCacheLock) {
    for (wdr <- webReaderCache.lookup(eid);
         ac  <- webReaderRefCount.lookup(eid);
         _ <- {Some(ac.incrementAndGet()) }) yield wdr
  }

  def leaseWebWriter(eid: UUID) = synchronizedRead(writerCacheLock) {
    for (wdw <- webWriterCache.lookup(eid);
         ac  <- webWriterRefCount.lookup(eid);
         _ <- {Some(ac.incrementAndGet()) }) yield wdw
  }

  def releaseWebReader(eid: UUID): Unit  = synchronizedWrite(readerCacheLock) {
    for (wdr <- webReaderCache.lookup(eid);
         ac <- webReaderRefCount.lookup(eid)) {
      if (ac.decrementAndGet() == 0) println("Do Cleanup!")
    }
  }

  def releaseWebWriter(eid: UUID): Unit  = synchronizedWrite(writerCacheLock) {
    for (wdr <- webWriterCache.lookup(eid);
         ac <- webWriterRefCount.lookup(eid)) {
      if (ac.decrementAndGet() == 0) println("Do Cleanup!")
    }
  }
}
