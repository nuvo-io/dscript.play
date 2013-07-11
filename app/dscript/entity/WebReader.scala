package dscript.entity


import play.api.libs.iteratee.{Enumerator, Concurrent}
import dds.prelude._
import dds._
import scala.collection.JavaConversions._

import dscript.json.JSON._
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID

class WebReader[T](val dr: org.omg.dds.sub.DataReader[T]) {

  val eid = UUID.randomUUID()

  private val count = new AtomicLong(0)
  private lazy val (out, channel) = Concurrent.broadcast[String]

  def connect(): Enumerator[String] = {
    if (count.getAndIncrement() == 0) start()
    out
  }
  def disconnect(): Unit = if (count.decrementAndGet() == 0)  stop()

  def start(): Unit =
    dr.listen {
      case DataAvailable(_) => dr.take().foreach(s => {
        val d = s.getData
        if (d != null) channel.push(toJson(d))
      })
    }

  def stop(): Unit = dr.deaf()

}
