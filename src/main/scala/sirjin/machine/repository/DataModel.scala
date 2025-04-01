package sirjin.machine.repository

import sirjin.machine.State.OperatingData
import sirjin.machine.proto.RawData
import spray.json.DefaultJsonProtocol._
import spray.json.JsonFormat
import spray.json.RootJsonFormat

import java.time.LocalDate
import java.util.UUID

object DataModel {
  case class MachineMapData(
      shopId: String = "0",
      ncId: UUID,
      opRate: Float = 0f,
      quantity: Int = 0,
      pgmNm: String = "",
      status: String = "",
      inCycleTime: Int = 0,
      waitTime: Int = 0,
      alarmTime: Int = 0,
      noconnTime: Int = 0,
      cycleTime: Int = 0,
      orderNumber: Option[String] = None,
      productNumber: Option[String] = None,
      isLast: Option[Boolean] = None
  )

  case class Routing(
      ncId: UUID,
      shopId: String = "1",
      program: String = "",
      orderNumber: String = "",
      productNumber: String = "",
      model: String = "",
      last: Option[Boolean] = None
  )

  case class MachineList(
      ncId: UUID,
      shopId: String,
      name: String,
      groupId: UUID,
      managementNo: String,
      pic: String,
      modelName: String,
      manufacturer: String,
      purchase: String,
      manufatureDate: LocalDate,
      purchaseDate: LocalDate,
      ncModel: String,
      ncType: String,
      ncSerialNo: String,
      use: String,
      dataGatheringType: String,
      remarks: String,
      regularCheck: String,
      regularCheckCycle: Int,
      imageFilePath: String,
  )
}

object OperatingMode {
  val OPERATE_READY      = "ready"
  val OPERATE_START      = "start"
  val OPERATE_FINISH     = "finish"
  val OPERATE_START_ERP  = "가공시작"
  val OPERATE_FINISH_ERP = "가공종료"
  val START_EVENT        = "start"
}

object NcType {
  val FANUC = "fanuc"
  val IO    = "io"
}

object MachineStatus {
  val CUTTING       = "CUTTING"
  val IN_CYCLE      = "IN-CYCLE"
  val WAIT          = "WAIT"
  val ALARM         = "ALARM"
  val NO_CONNECTION = "NO-CONNECTION"
}

object ProgramChangeType {
  val START  = "start"
  val END    = "end"
  val CHANGE = "change"
}

object ReturnMessage {
  val SUCCESS = "success"
}

object PartCountEventType {
  val AUTO   = "auto"
  val MANUAL = "manual"
}

object AlarmTy {
  val OCCURRED = "occurred"
  val FIXED    = "fixed"
}

object HttpDataModel {
  case class AuxCode(
      T: String = ""
  )
  implicit val AuxCodeFormat: RootJsonFormat[AuxCode] = jsonFormat1(AuxCode)

  case class ErpRequestBody(
      id: String,
      program_start_time: String,
      program_end_time: String,
      nc_id: String,
      model: String,
      orderNumber: String,
      productNumber: String
  )
  implicit val ErpDataFormat: JsonFormat[ErpRequestBody] = jsonFormat7(ErpRequestBody)

  case class _State(
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
      partNumber: Option[String] = None,
      partCount: Int = 0,
      quantity: Int = 0,
      totalPartCount: Int = 0,
      cuttingTime: Int = 0,
      opRate: Float = 0f,
      timestamp: Long = System.currentTimeMillis(),
      toolNumber: String = "-1",
      spdLd: Float = 0f,
      cycleStartTime: String,
      operateData: OperatingData,
      alarm: Seq[AlarmData] = Seq.empty
  )
  implicit val StateDataFormat: RootJsonFormat[_State] = jsonFormat22(_State)

  case class PathData(
      path: Int = 1,
      spindleLoad: Float = 0.0f,
      spindleOverride: Int = 0,
      spindleSpeed: Float = 0.0f,
      feedOverride: Int = 0,
      auxCodes: AuxCode
  )
  implicit val PathDataFormat: RootJsonFormat[PathData] = jsonFormat6(PathData)

  case class AlarmData(
      `type`: String = "",
      alarmCode: String = "",
      alarmMessage: String = "",
  )
  implicit val AlarmDataFormat: RootJsonFormat[AlarmData] = jsonFormat3(AlarmData)

  case class MachineRawData(
      shopId: Int = 0,
      ncId: String = "",
      timestamp: Long = 0L,
      partCount: Int = 0,
      totalPartCount: Int = 0,
      mainPgmNm: String = "",
      status: String = "",
      mode: String = "",
      pathData: Seq[PathData] = Seq.empty,
      alarms: Seq[AlarmData] = Seq.empty
  ) {
    def resolvedStatus: String = alarms match {
      case Nil =>
        if (status == "START" && pathData.head.spindleLoad > 0) {
          MachineStatus.CUTTING
        } else if (status == "START") {
          MachineStatus.IN_CYCLE
        } else {
          MachineStatus.WAIT
        }
      case _ => MachineStatus.ALARM
    }

    def convertToProtoDataType: RawData = {
      RawData(
        shopId = shopId,
        ncId = ncId,
        timestamp = timestamp,
        partCount = partCount,
        totalPartCount = totalPartCount,
        mainPgmNm = mainPgmNm,
        status = status,
        mode = mode,
        pathData = Seq(
          sirjin.machine.proto.PathData(
            path = pathData.head.path.toString,
            spindleLoad = pathData.head.spindleLoad,
            spindleOverride = pathData.head.spindleOverride,
            spindleSpeed = pathData.head.spindleSpeed,
            feedOverride = pathData.head.feedOverride,
            auxCodes = Some(sirjin.machine.proto.AuxCode(t = pathData.head.auxCodes.T))
          )
        ),
        alarms = alarms.map(alarm =>
          sirjin.machine.proto.AlarmData(
            `type` = alarm.`type`,
            alarmCode = alarm.alarmCode,
            alarmMessage = alarm.alarmMessage
          )
        )
      )
    }
  }

  implicit val MachineRawDataFormat: RootJsonFormat[MachineRawData] = jsonFormat10(MachineRawData)
}
