package sirjin.machine

import akka.persistence.journal.{ Tagged, WriteEventAdapter }

class TaggingEventAdapter extends WriteEventAdapter {
  override def manifest(event: Any): String = ""

  override def toJournal(event: Any): Any = event match {
    case e: MachineEvent =>
      Tagged(e, Set(s"ncId-${math.abs(e.ncId.hashCode % MachineState.tags.size)}"))
    case other => other
  }
} 