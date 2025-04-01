package sirjin.machine

import sirjin.machine.proto.AlarmData
import sirjin.machine.repository.MachineStatus

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed trait MachineEvent extends CborSerializable {
  def ncId: String
}

object MachineEvent {
  case class MachineDataUpdated(
      shopId: String = "0",
      ncId: String = "",
      cuttingTime: Int = 0,
      inCycleTime: Int = 0,
      waitTime: Int = 0,
      alarmTime: Int = 0,
      noConnectionTime: Int = 0,
      lastStatus: String = MachineStatus.NO_CONNECTION,
      lastStatusTime: Long = 0,
      mainPgmNm: String = "",
      status: String = MachineStatus.NO_CONNECTION,
      alarms: Seq[AlarmData] = Seq.empty,
      partNumber: Option[String] = None,
      opRate: Float = 0f,
      path: Int = 0,
      partCount: Int = 0,
      quantity: Int = 0,
      totalPartCount: Int = 0,
      timestamp: Long = System.currentTimeMillis(),
      toolNumber: String = "-1",
      spdLd: Float = 0f,
      cycleStartTime: LocalDateTime = LocalDateTime.parse(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)),
      startOperating: Boolean = false
  ) extends MachineEvent

  case class ProgramUpdated(
      shopId: String = "",
      ncId: String = "",
      prevProgram: String = "",
      currentProgram: String = "",
      programStartTime: LocalDateTime =
        LocalDateTime.parse(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)),
      programEndTime: LocalDateTime = LocalDateTime.parse(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)),
      eventType: String = "",
      orderNumber: String = "",
      productNumber: String = "",
      model: String = "",
      last: Option[Boolean] = None
  ) extends MachineEvent

  case class PartCountUpdated(
      ncId: String,
      datetime: LocalDateTime = LocalDateTime.parse(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)),
      shopId: String = "",
      quantity: Int = 0,
      totalQuantity: Int = 0,
      dailyPartCount: Int = 0,
      orderNumber: String = "",
      productNumber: String = "",
      model: String = "",
      program: String = "",
      programStartTime: LocalDateTime =
        LocalDateTime.parse(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)),
      programEndTime: LocalDateTime = LocalDateTime.parse(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)),
      last: Option[Boolean] = None,
  ) extends MachineEvent

  case class TimeChartDataUpdated(
      shopId: String,
      ncId: String,
      datetime: LocalDate,
      status: String,
      regdt: LocalDateTime,
      pgmNm: String,
      spdLd: Float,
  ) extends MachineEvent

  case class OperatingDataUpdated(
      ncId: String = "",
      orderNum: String = "",
      partNum: String = "",
      ncType: String = "",
      isLast: Option[Boolean] = None,
      isStart: Boolean = false,
      eventType: String = "",
      name: String = "",
      programStartTime: LocalDateTime =
        LocalDateTime.parse(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)),
  ) extends MachineEvent

  case class NoConnection(
      ncId: String,
      status: String,
      lastStatus: String,
      timestamp: Long
  ) extends MachineEvent

  case class OperatingRateUpdated(
      ncId: String,
      opRate: Float
  ) extends MachineEvent

  case class SummaryDataUpdated(
      shopId: String,
      ncId: String,
      quantity: Int,
      cycleTime: Int,
      inCycleTime: Int,
      waitTime: Int,
      alarmTime: Int,
      noconnTime: Int,
      opRate: Float
  ) extends MachineEvent

  case class AlarmDataUpdated(
      shopId: String,
      ncId: String,
      alarms: Seq[AlarmData],
      alarmTy: String
  ) extends MachineEvent
}
