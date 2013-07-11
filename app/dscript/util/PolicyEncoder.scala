package dscript.util

import org.omg.dds.core.policy._
import java.util.concurrent.TimeUnit

object PolicyEncoder {
  def encode(p: QosPolicy): String = p match {
    case r: Reliability => if (r.getKind == Reliability.Kind.BEST_EFFORT) "Re(B)" else "Re(R)"
    case d: Durability => d.getKind match {
      case Durability.Kind.VOLATILE => "Du(V)"
      case Durability.Kind.TRANSIENT_LOCAL => "Du(TL)"
      case Durability.Kind.TRANSIENT => "Du(T)"
      case Durability.Kind.PERSISTENT => "Du(P)"
    }
    case tbf: TimeBasedFilter => {
      val  d = tbf.getMinimumSeparation().getDuration(TimeUnit.MILLISECONDS)
      s"TF($d)"
    }
    case p: Partition => {
      import scala.collection.JavaConversions._
      ("P(" /: p.getName) (_ + ":" + _) + ")"
    }
    case h: History =>
      if (h.getKind == History.Kind.KEEP_ALL) "H(*)" else "H(" + h.getDepth + ")"

    case  _ => "?(?)"
  }
  def encode(ps: List[QosPolicy]): String = {
    val sps = ps.sortWith((p, q) => p.getClass.getName < q.getClass.getName)
    val s = ("" /: sps)(_ + encode(_))
    println(s"Encoded Poloicies: $s")
    s
  }
}
