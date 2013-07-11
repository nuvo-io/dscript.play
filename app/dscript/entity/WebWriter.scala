package dscript.entity

import dscript.json.JSON._
import akka.actor.{Props, Actor}
import dscript.runtime.DSRuntime._
import java.util.UUID

case class WriteData(msg: String)

class WebWriter[T](val dw: org.omg.dds.pub.DataWriter[T]) {

  val eid = UUID.randomUUID()

  def connect = (s: String) => { actor ! WriteData(s) }

  /**
   * Dispatches data for this WebWriter
   */
  class WebWriterActor extends Actor {
    def receive = {
      case WriteData(m) => {
        //println(s"Writing: $m")
        val cls = dw.getTopic.getTypeSupport.getType
        val s = fromJson(m, cls)
        dw.write(s)
      }
    }
  }
  val actor = system.actorOf(Props(new WebWriterActor), name = "WebWriterActor" + dw.hashCode())
}
