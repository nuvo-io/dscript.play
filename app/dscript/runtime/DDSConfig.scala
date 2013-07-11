package dscript.runtime

object DDSConfig {
  val properties = List(
    ("dds.listeners.useTransportThread", "false"),
    ("ddsi.timer.threadPool.size", "1"),
    ("ddsi.receiver.threadPool.size", "1"),
    ("ddsi.dataProcessor.threadPool.size", "1"),
    ("ddsi.acknackProcessor.threadPool.size", "1"),
    ("ddsi.receiver.udp.buffer.size", "8192"),
    ("ddsi.participant.leaseDuration", "300000")
  )


  def setup() {
    properties foreach { p => System.setProperty(p._1, p._2) }
  }

}