package dscript.controllers

import scala.language.implicitConversions

import play.api.libs.json._
import play.api.libs.iteratee.{Iteratee, Enumerator}
import org.omg.dds.sub.DataReaderQos
import org.omg.dds.pub.DataWriterQos
import org.omg.dds.core.policy.QosPolicy
import java.util.UUID
import dds._
import dds.config.DefaultEntities._

object DSEntityKind {
  val Topic       = 0
  val DataReader  = 1
  val DataWriter  = 2
}

object CommandId {
  val OK            = 0
  val Error         = 1
  val Create        = 2
  val Delete        = 3
  val Unregister    = 4
}

/**
 * The structure of commands could be as follows:
 *   DSHeader | DSBody
 *
 *   Where:
 *      DSHeader = (cid, ek)
 *      DSBody = content specific to the command
 */

case class DSHeader(cid: Int, ek: Int, sn: Int)
abstract class DSBody
case class DSTopicInfo(did: Int, topicName: String, topicType: String, qos: List[QosPolicy]) extends DSBody
case class DSEntityId(eid: UUID) extends DSBody
case class DSError(msg: String) extends DSBody

case class DSCommand(h: DSHeader, b: DSBody)

object prelude {

  object PolicyId {

    val History =          0
    val Reliability =      1
    val Partition =        2
    val ContentFilter =    3
    val TimeFilter =       4
    val Durability=        5
  }

  object HistoryKind {
    val KeepAll = 0
    val KeepLast = 1
  }
  object ReliabilityKind {
    val Reliable = 0
    val BestEffort = 1
  }
  object DurabilityKind {
    val Volatile = 0
    val TransientLocal = 1
    val Transient = 2
    val Persistent = 3
  }

  def parsePolicy(js: JsValue, id: Int): Option[QosPolicy] = id match {
    case PolicyId.History =>
      for (k <- (js \ "k").asOpt[Int];
           v <- (js \ "v").asOpt[Int].orElse(Some(1));
           p <- if (k == HistoryKind.KeepLast) Some(History.KeepLast(v))
                else if (k == HistoryKind.KeepAll) Some(History.KeepAll())
                else None) yield p

    case PolicyId.Reliability =>
      for (k <- (js \ "k").asOpt[Int];
           p <- if (k == ReliabilityKind.Reliable) Some(Reliability.Reliable)
                else if (k == ReliabilityKind.BestEffort) Some(Reliability.BestEffort)
                else None) yield p

    case PolicyId.Partition => {
      for (k <- (js \ "vs").asOpt[JsArray];
           plist <- Some((k.value.map(e => e.as[String])).toList);
           p <- Some(Partition(plist));
           _ <- {println(s"plist: $plist"); Some(1)}
          ) yield p

    }
    case PolicyId.Durability =>
      for (k <- (js \ "k").asOpt[Int];
           p <- if (k == DurabilityKind.Volatile) Some(Durability.Volatile)
           else if (k == DurabilityKind.TransientLocal) Some(Durability.TransientLocal)
           else if (k == DurabilityKind.Transient) Some(Durability.Transient)
           else if (k == DurabilityKind.Persistent) Some(Durability.Persistent)
           else None) yield p

    case PolicyId.TimeFilter =>
      for (d <- (js \ "v").asOpt[Int];
          p <- Some(TimeBasedFilter(d))) yield p

    case PolicyId.ContentFilter =>
      for (d <- (js \ "v").asOpt[String];
           p <- Some(ContentFilter(d))
      ) yield p

    case _ => None
  }



  def parseQos(ps: JsArray): Option[List[QosPolicy]] = {
    val jpolicies = ps.value
    val policies = {
      for (jp <- jpolicies;
           id <- (jp \ "id").asOpt[Int];
           p <- parsePolicy(jp, id)) yield p}

    Some(policies.toList)
  }

  def parseHeader(h: JsValue): Option[DSHeader] =
    for (cid <- (h \ "cid").asOpt[Int];
         eid <- (h \ "ek").asOpt[Int];
         sn  <- (h \ "sn").asOpt[Int]) yield DSHeader(cid, eid, sn)



  def parseBody(h: DSHeader, jb: JsValue): Option[DSBody] =  (h.cid, h.ek) match {
    case (2, _) => {
      for (
        did <- (jb \ "did").asOpt[Int];
        tn <- (jb \ "tn").asOpt[String];
        tt <- (jb \ "tt").asOpt[String];
        jqos <- (jb \ "qos").asOpt[JsArray];
        qos <- parseQos(jqos)) yield DSTopicInfo(did, tn, tt, qos)
    }
  }

  implicit val createCommand = new Reads[DSCommand] {
    def reads(json: JsValue): JsResult[DSCommand] = {
      for (
        jh <- (json \ "h").asOpt[JsObject];
        h <- parseHeader(jh);
        jb <- (json \ "b").asOpt[JsObject];
        b <- parseBody(h, jb)) yield JsSuccess(DSCommand(h, b))
    }.getOrElse(JsError("Invalid command"))
  }


  def writeHeader(h: DSHeader): JsValue = Json.obj(
    "cid" -> JsNumber(h.cid),
    "ek" -> JsNumber(h.ek),
    "sn" -> JsNumber(h.sn)
  )



  def writeEntityId(e: DSEntityId): JsValue = Json.obj("eid" -> JsString(e.eid.toString))



  def writeError(e: DSError): JsValue = Json.obj("msg" -> JsString(e.msg))

  def writeBody(b: DSBody) = b match {
    case e: DSError => writeError(e)
    case i: DSEntityId => writeEntityId(i)
  }

  implicit val commandWrites = new Writes[DSCommand] {
    def writes(c: DSCommand): JsValue = Json.obj(
      "h" -> writeHeader(c.h),
      "b" -> writeBody(c.b))
  }
}
