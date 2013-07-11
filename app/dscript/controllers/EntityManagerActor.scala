package dscript.controllers

import akka.actor.{Props, PoisonPill, Actor}
import akka.event.Logging
import dds.prelude._
import dds._
import play.api.libs.iteratee.{Concurrent, Enumerator}
import dds.config.env
import dscript.controllers.prelude._
import play.api.libs.json.Json
import dscript.entity.{WebWriter, WebReader}
import org.omg.dds.core.policy.QosPolicy
import dscript.runtime.DSRuntime._
import dscript.util.{PolicyEncoder, Cache}
import java.util.concurrent.atomic.AtomicInteger
import dscript.synchronizers._

case object Cleanup;

class EntityManagerActor(val out: Enumerator[String], channel: Concurrent.Channel[String]) extends Actor {
  val log = Logging(context.system, this)

  var cleanUpActions = List[()=> Any]()

  private def loadClass(name: String): Option[Class[_ <: Any]] =
    try {
      Some(Class.forName(name))
    } catch {
      case e: ClassNotFoundException => {
        log.warning(s"Unable to load class: $name")
        None
      }
    }

  private def lookupParticipant(did: Int): Option[org.omg.dds.domain.DomainParticipant] = {
    participantCache.lookup(did).orElse {
      if (did < 0) None
      else {
        val dp = DomainParticipant(did)
        participantCache.add(did, dp)
        Some(dp)
      }
    }
  }


  private def lookupTopic(did: Int, topicName: String, cls: Class[_ <: Any])(implicit dp: org.omg.dds.domain.DomainParticipant) = {
    topicCache.lookup((did, topicName)).orElse {
      val t = Topic[Any](topicName)(Manifest.classType(cls), dp)
      topicCache.add((did, topicName), t)
      Some(t)
    }
  }



  private def createWebReader(did: Int, topicName: String, topicType: String, qos: List[QosPolicy]): Option[WebReader[_ <: Any]] = {
    import dds.config.DefaultEntities.defaultPolicyFactory
    for (
      p <- lookupParticipant(did);
      cls <- loadClass(topicType);
      topic <- lookupTopic(did, topicName, cls)(p)) yield {

      val (sps, drps) = qos.partition({
        case p: org.omg.dds.core.policy.Partition => {
          import scala.collection.JavaConversions._
          p.getName.foreach(println)
          true
        }
        case _ => false
      })

      // Encode the Policies so that we can use them for entity look-up and matching
      val spe = PolicyEncoder.encode(sps)
      val dre = PolicyEncoder.encode(drps)
      val entityKey = topicName + ":" + dre

      implicit val dp = p
      val subc = synchronizedWrite(subCacheLock) {
        subCache.lookup((did, spe)).orElse {
          val subQos = SubscriberQos().withPolicies(sps.asInstanceOf[List[QosPolicy.ForSubscriber]] :_*)
          val sub = Subscriber(subQos)
          val cache = new Cache[String, WebReader[_ <: Any]]
          subCache.add((did, spe), (sub, cache))
          Some((sub, cache))
        }
      }


      (for (
        (sub, cache ) <- subc;
        wdr <- synchronizedWrite(readerCacheLock){cache.lookup(entityKey).orElse {
          log.info(s"Web Reader Not Found, creating one for: $entityKey")
          implicit  val s = sub
          val drqos = DataReaderQos().withPolicies(drps.asInstanceOf[List[QosPolicy.ForDataReader]] :_*)
          val dr = DataReader(topic, drqos)
          val wdr = new WebReader(dr)
          println("Adding: " + entityKey)
          cache.add(entityKey, wdr)
          subCache.add((did, spe), (sub, cache))
          val eid = wdr.eid
          synchronizedWrite(readerCacheLock) {
            webReaderCache.add(eid, wdr)
            webReaderRefCount.add(eid, new AtomicInteger(0))
          }
          Some(wdr)
        } };
        cleanup <- Some(() => {
          synchronizedWrite(readerCacheLock) {
            webReaderRefCount.lookup(wdr.eid).map{ ac =>
              val c = ac.decrementAndGet()
              log.info(s"Reader RefCount = $c")
              if (c == 0) {
                webReaderRefCount.evict(wdr.eid)
                println("Evicting: " + entityKey)
                cache.evict(entityKey).map { e =>
                  log.info("Closing Data Reader!" + entityKey)
                  e.dr.close()
                }
                if (cache.size == 0)
                  synchronizedWrite(subCacheLock) {
                    subCache.evict(did, spe).map(_._1.close())
                  }
              }
            }
          }
        });
        _ <- {cleanUpActions = cleanup :: cleanUpActions; Some(1) }
      ) yield wdr).get
    }
  }.orElse(None)


  private def createWebWriter(did: Int, topicName: String, topicType: String, qos: List[QosPolicy]): Option[WebWriter[_ <: Any]] = {
    for (
      p <- lookupParticipant(did);
      cls <- loadClass(topicType);
      topic <- lookupTopic(did, topicName, cls)(p)) yield {
      val (pps, dwps) = qos.partition({
        case p: org.omg.dds.core.policy.Partition => {
          import scala.collection.JavaConversions._
          p.getName.foreach(println)
          true
        }
        case _ => false
      })
      implicit val dp = p
      // Encode the Policies so that we can use them for entity look-up and matching
      val ppe = PolicyEncoder.encode(pps)
      val dwe = PolicyEncoder.encode(dwps)

      val pubc = synchronizedWrite(pubCacheLock) {
        pubCache.lookup((did, ppe)).orElse {
          val pubQos = PublisherQos().withPolicies(pps.asInstanceOf[List[QosPolicy.ForPublisher]] :_*)
          val pub = Publisher(pubQos)
          val cache = new Cache[String, WebWriter[_ <: Any]]
          pubCache.add((did, ppe), (pub, cache))
          Some((pub, cache))
        }
      }

      (for (
        (pub, cache) <- pubc;
        wdw <- synchronizedWrite(writerCacheLock) { cache.lookup(topicName + ":" + dwe).orElse {
          implicit val p = pub
          val dwQos = DataWriterQos().withPolicies(dwps.asInstanceOf[List[QosPolicy.ForDataWriter]] :_*)
          val dw = DataWriter(topic, dwQos)
          val wdw = new WebWriter(dw)
          cache.add(topicName + ":" + dwe, wdw)
          val eid = wdw.eid
          webWriterCache.add(eid, wdw)
          webReaderRefCount.add(eid, new AtomicInteger(1))
          Some(wdw)
        }};
        cleanup <- Some(() => {
          synchronizedWrite(writerCacheLock) {
            webReaderRefCount.lookup(wdw.eid).map{ ac =>
              val c = ac.decrementAndGet()
              log.info(s"Writer RefCount = $c")
              if (c == 0) {
                webReaderRefCount.evict(wdw.eid)
                cache.evict(topicName + ": " + dwe).map {
                  _.dw.close()
                }
                if (cache.size == 0)
                  synchronizedWrite(pubCacheLock) {
                    pubCache.evict(did, ppe).map(_._1.close())
                  }
              }
            }
          }
        });
        _ <- {cleanUpActions = cleanup :: cleanUpActions; Some(1) }
      ) yield wdw).get
    }
  }.orElse(None)


  def receive = {
    case DSCommand(h, DSTopicInfo(did, topicName, topicType, qos)) => {
      val cmd = h.ek match {
        case DSEntityKind.DataReader => {
          readerCache.lookup((did, topicName)).orElse(createWebReader(did, topicName, topicType, qos)) match {
            case Some(wdr) => {
              DSCommand(
                DSHeader(CommandId.OK, h.ek, h.sn),
                DSEntityId(wdr.eid))
            }
            case None => {
              DSCommand(
                DSHeader(CommandId.Error, h.ek, h.sn),
                DSError("Unable to execute command"))
            }
          }

        }
        case DSEntityKind.DataWriter => {
          writerCache.lookup((did, topicName)).orElse(createWebWriter(did, topicName, topicType, qos)) match {
            case Some(wdw) => {
              DSCommand(
                DSHeader(CommandId.OK, h.ek, h.sn),
                DSEntityId(wdw.eid))
            }
            case None => {
              DSCommand(
                DSHeader(CommandId.Error, h.ek, h.sn),
                DSError("Unable to execute command"))
            }
          }
        }
      }
      val scmd = Json.stringify(Json.toJson(cmd))
      log.info(s"Replaying with Command: $scmd")
      channel.push(scmd)
    }

    case Cleanup => {
      println("Cleaning up")
      cleanUpActions.foreach(a => a())
    }
    case _ => log.warning("Unhandled Message")
  }
}
