package sirjin.machine.services

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.util.Timeout
import org.slf4j.LoggerFactory
import sirjin.machine.MachineCommand.UpdateOperationState
import sirjin.machine.MachineCommand.UpdateProgramStatus
import sirjin.machine.MachineEvent.ProgramUpdated
import sirjin.machine.State.OperatingData
import sirjin.machine.proto.ResBody
import sirjin.machine.repository.DataModel.MachineList
import sirjin.machine.repository.DataModel.Routing
import sirjin.machine.repository.HttpDataModel.ErpRequestBody
import sirjin.machine.repository.HttpDataModel
import sirjin.machine.repository.MachineStatus
import sirjin.machine.repository.OperatingMode
import sirjin.machine.repository.ProgramChangeType
import sirjin.machine._
import spray.json.enrichAny

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ProgramServiceImpl(system: ActorSystem[_], routingService: RoutingService) extends ProgramService {
  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout     = Timeout(5.seconds)
  private val logger                = LoggerFactory.getLogger(getClass)

  def entityRef(id: String): EntityRef[MachineCommand] = {
    val key = s"$id"

    val sharding = ClusterSharding(system)
    sharding.entityRefFor(MachineState.typeKey, key)
  }

  override def compareWithPevProgram(raw: HttpDataModel.MachineRawData, state: State): Future[Unit] = {
    for {
      _ <- {
        val changeCd = getProgramChangeCode(state, raw)
        changeCd match {
          case ProgramChangeType.START => // 가공 시작
            changeOperatingState(
              state,
              raw.mainPgmNm,
              OperatingMode.OPERATE_START_ERP,
              changeCd
            )
          case ProgramChangeType.END => // 가공 완료
            changeOperatingState(
              state,
              raw.mainPgmNm,
              OperatingMode.OPERATE_FINISH_ERP,
              changeCd
            )
          case ProgramChangeType.CHANGE => // 가공 프로그램 변경
            for {
              _ <- changeOperatingState(
                state,
                raw.mainPgmNm,
                OperatingMode.OPERATE_FINISH_ERP,
                ProgramChangeType.END,
                isPgmChange = true
              )
              _ <- changeOperatingState(
                state,
                raw.mainPgmNm,
                OperatingMode.OPERATE_START_ERP,
                ProgramChangeType.START
              )
            } yield ()
          case _ => Future.unit
        }
      }
    } yield ()
  }

  def updateProgramEvent(
      programUpdatedEvent: MachineEvent.ProgramUpdated,
      programChangeType: String
  ): Future[ResBody] = {
    val programEvent = programChangeType match {
      case ProgramChangeType.START =>
        logger.info("[가공 시작] {},{}", programUpdatedEvent.ncId, programUpdatedEvent.currentProgram)

        updateOperatingState(programUpdatedEvent)

        programUpdatedEvent.copy(
          programEndTime = programUpdatedEvent.programStartTime,
          eventType = ProgramChangeType.START,
        )
      case ProgramChangeType.END =>
        logger.info("[가공 종료] {},{}", programUpdatedEvent.ncId, programUpdatedEvent.currentProgram)

        updateOperatingState(
          ProgramUpdated(
            shopId = programUpdatedEvent.shopId,
            ncId = programUpdatedEvent.ncId,
          )
        )

        val localDateTime = LocalDateTime.parse(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
        programUpdatedEvent.copy(
          currentProgram = programUpdatedEvent.prevProgram,
          programEndTime = localDateTime,
          eventType = ProgramChangeType.END,
        )
      case ProgramChangeType.CHANGE =>
        logger.info("[가공 변경] {},{}", programUpdatedEvent.ncId, programUpdatedEvent.currentProgram)

        programUpdatedEvent.copy(
          programStartTime = LocalDateTime.parse(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)),
          programEndTime = LocalDateTime.parse(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)),
          eventType = ProgramChangeType.CHANGE,
        )
    }

    entityRef(programEvent.ncId).ask(replyTo => UpdateProgramStatus(programEvent, replyTo))
  }

  def updateOperatingState(programUpdatedEvent: ProgramUpdated): Future[ResBody] = {
    entityRef(programUpdatedEvent.ncId).ask(replyTo =>
      UpdateOperationState(
        OperatingData(
          orderNum = programUpdatedEvent.orderNumber,
          name = programUpdatedEvent.model,
          partNum = programUpdatedEvent.productNumber,
          isLast = programUpdatedEvent.last,
          programStartTime = programUpdatedEvent.programStartTime.toString
        ),
        replyTo
      )
    )
  }

  def getProgramChangeCode(state: State, raw: HttpDataModel.MachineRawData): String = {
    val currentStatus = raw.resolvedStatus
    if (state.shopId == "") {
      "no state"
    } else if (raw.mainPgmNm == "") {
      "io"
    } else if (
      (state.status == MachineStatus.WAIT || state.status == MachineStatus.ALARM)
      && (currentStatus == MachineStatus.IN_CYCLE || currentStatus == MachineStatus.CUTTING)
    ) {
      ProgramChangeType.START
    } else if (
      (state.status == MachineStatus.IN_CYCLE || state.status == MachineStatus.CUTTING || state.operateData.orderNum != "")
      && (currentStatus == MachineStatus.WAIT || currentStatus == MachineStatus.ALARM)
    ) {
      ProgramChangeType.END
    } else if (
      (currentStatus == MachineStatus.CUTTING || currentStatus == MachineStatus.IN_CYCLE)
      && (state.mainPgmNm != raw.mainPgmNm)
    ) {
      ProgramChangeType.CHANGE
    } else {
      ""
    }
  }

  def changeOperatingState(
      state: State,
      currentProgram: String,
      operatingMode: String,
      programChangeType: String,
      isPgmChange: Boolean = false
  ): Future[Unit] = {
    for {
      routing <- {
        val program = if (isPgmChange) {
          state.mainPgmNm
        } else {
          currentProgram
        }
        routingService.checkExistRouting(state.ncId, program)
      }
    } yield {
      routing match {
        case Some((routing, machineList)) =>
          logger.info("### Routing 있음 ###")
          val datetime = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
          for {
            resp <- sendDataToERP((routing, machineList), operatingMode, state, datetime)
            _    <- Future { logger.info("HTTP RESPONSE : {}", resp) }
            _ <- {
              val programData = ProgramUpdated(
                shopId = state.shopId,
                ncId = state.ncId,
                prevProgram = state.mainPgmNm,
                currentProgram = currentProgram,
                orderNumber = routing.orderNumber,
                productNumber = routing.productNumber,
                model = routing.model,
                last = routing.last,
                programStartTime = LocalDateTime.parse(datetime)
              )

              updateProgramEvent(programData, programChangeType)
            }
          } yield ()
        case _ =>
          logger.info("### Routing 없음 ###")
          for {
            _ <- {
              val programData = ProgramUpdated(
                shopId = state.shopId,
                ncId = state.ncId,
                prevProgram = state.mainPgmNm,
                currentProgram = currentProgram,
              )
              updateProgramEvent(programData, programChangeType)
            }
          } yield ()
      }
    }
  }

  def sendDataToERP(
      routing: (Routing, MachineList),
      operateType: String,
      state: State,
      datetime: String
  ): Future[HttpResponse] = {
    logger.info("SEND ERP : {}, {}", operateType, routing)
    val programEndTime = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

    val time = operateType match {
      case OperatingMode.OPERATE_START_ERP  => (datetime, datetime)
      case OperatingMode.OPERATE_FINISH_ERP => (state.operateData.programStartTime, programEndTime)
    }

    Http(system).singleRequest(
      HttpRequest(
        method = HttpMethods.POST,
        uri = scala.sys.env("ERP_URL"),
        entity = HttpEntity(
          ContentTypes.`application/json`,
          ErpRequestBody(
            id = operateType,
            program_start_time = time._1,
            program_end_time = time._2,
            nc_id = routing._1.ncId.toString,
            model = routing._1.model,
            orderNumber = routing._1.orderNumber,
            productNumber = routing._1.productNumber
          ).toJson.toString()
        )
      )
    )
  }
}
