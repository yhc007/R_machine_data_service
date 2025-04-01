package sirjin.machine

import sirjin.machine.State.MachineOperationTime
import sirjin.machine.State.OperatingData
import sirjin.machine.proto.AlarmData
import sirjin.machine.repository.MachineStatus
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object State {
  val statusOrderMap: Map[String, Int] = Map(
    MachineStatus.NO_CONNECTION -> 1,
    MachineStatus.IN_CYCLE      -> 2,
    MachineStatus.CUTTING       -> 2,
    MachineStatus.WAIT          -> 3,
    MachineStatus.ALARM         -> 4
  )

  case class PartCountData(
      totalPartCount: Int = 0,
      orderNumber: String = "",
      productNumber: String = "",
      model: String,
      program: String,
      programStartTime: LocalDateTime,
      programEndTime: LocalDateTime,
      last: Option[Boolean] = None
  )

  case class MachineOperationTime(
      inCycleTime: Int = 0,
      waitTime: Int = 0,
      alarmTime: Int = 0,
      noConnectionTime: Int = 0,
      cuttingTime: Int = 0,
  ) extends CborSerializable

  case class OperatingData(
      orderNum: String = "",
      name: String = "",
      partNum: String = "",
      ncType: String = "",
      isLast: Option[Boolean] = None,
      eventType: String = "",
      programStartTime: String = ""
  ) extends CborSerializable
  implicit val OperatingDataFormat: RootJsonFormat[OperatingData] = jsonFormat7(OperatingData)
}

case class State(
    shopId: String = "",
    ncId: String = "",
    inCycleTime: Int = 0,
    waitTime: Int = 0,
    alarmTime: Int = 0,
    noConnectionTime: Int = 0,
    status: String = MachineStatus.NO_CONNECTION,
    mainPgmNm: String = "",
    lastStatus: String = MachineStatus.NO_CONNECTION,
    lastStatusTime: Long = 0,
    alarms: Seq[AlarmData] = Seq.empty,
    partNumber: Option[String] = None,
    partCount: Int = 0,
    quantity: Int = 0,
    totalPartCount: Int = 0,
    cuttingTime: Int = 0,
    opRate: Float = 0f,
    timestamp: Long = System.currentTimeMillis(),
    toolNumber: String = "-1",
    spdLd: Float = 0f,
    cycleStartTime: LocalDateTime = LocalDateTime.parse(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)),
    operateData: OperatingData = OperatingData()
) extends CborSerializable {

  def nextMachineOperationTime(): MachineOperationTime = {
    val prev     = MachineOperationTime(inCycleTime, waitTime, alarmTime, noConnectionTime, cuttingTime)
    val timeDiff = 120
    lastStatus match {
      case MachineStatus.IN_CYCLE      => prev.copy(inCycleTime = inCycleTime + timeDiff)
      case MachineStatus.WAIT          => prev.copy(waitTime = waitTime + timeDiff)
      case MachineStatus.ALARM         => prev.copy(alarmTime = alarmTime + timeDiff)
      case MachineStatus.CUTTING       => prev.copy(cuttingTime = cuttingTime + timeDiff)
      case MachineStatus.NO_CONNECTION => prev.copy(noConnectionTime = noConnectionTime + timeDiff)
      case _                           => prev
    }
  }
}
