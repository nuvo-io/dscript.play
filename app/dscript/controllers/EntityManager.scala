package dscript.controllers

import play.api.mvc._
import play.api.libs.iteratee.{Concurrent, Iteratee, Enumerator}
import play.api.libs.json._
import dscript.controllers.prelude._
import akka.actor.{PoisonPill, Props}
import java.util.UUID
import dscript.runtime.DSRuntime._

import scala.concurrent._
import ExecutionContext.Implicits.global

object EntityManager extends Controller {

  def index = Action {
    Ok(views.html.index("DDScript is up and Running."))
  }

  def controller = WebSocket.using[String] {
    request => {
      val (out, channel) = Concurrent.broadcast[String]
      val manager = system.actorOf(Props(new EntityManagerActor(out, channel)), name = "EntityManagerActor" + request.hashCode())
      val in = Iteratee.foreach[String](m => {
        println(s"Received: $m")
        val jsv = Json.parse(m)
        val cmd = jsv.as[DSCommand]
        println(s"DSCommand: $cmd")
        manager ! cmd
      }).mapDone(_ => {
        println(">>> Connection Closed by remote peer. Stopping associated Actor")
        manager ! Cleanup
        manager ! PoisonPill
      })
      (in, out)
    }
  }

  def reader(uuid: String) = WebSocket.using[String] {
    request => {
      println(s"Received Request to Bind Reader: $uuid")
      val eid = UUID.fromString(uuid)
      webReaderRefCount.lookup(eid).map(_.incrementAndGet())
      webReaderCache.lookup(eid).map { wdr =>
        println("Found WebReader")
        val in = Iteratee.ignore[String].mapDone(_ => wdr.disconnect())
        (in, wdr.connect())
      }.getOrElse((Iteratee.ignore[String], Enumerator.eof[String]))
    }
  }

  def writer(uuid: String) = WebSocket.using[String] {
    request => {
      println(s">> Received Request to Bind Writer: $uuid")
      val eid = UUID.fromString(uuid)
      webWriterRefCount.lookup(eid).map(_.incrementAndGet())
      webWriterCache.lookup(eid).map { wwr =>
        println(">> Found WebWriter")
        val link = wwr.connect
        val in = Iteratee.foreach[String](m => {
          // println(s"received: $m")
          link(m)
        })
        val (out, channel) = Concurrent.broadcast[String]
        (in, out)
      }.getOrElse((Iteratee.ignore[String], Enumerator.eof[String]))
    }
  }
}
