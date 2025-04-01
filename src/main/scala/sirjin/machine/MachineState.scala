package sirjin.machine

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.EntityContext
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.RecoveryFailed
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.scaladsl.ReplyEffect
import akka.persistence.typed.scaladsl.RetentionCriteria
import io.getquill.PostgresAsyncContext
import io.getquill.SnakeCase
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sirjin.machine.State.OperatingData
import sirjin.machine.proto.ResBody
import sirjin.machine.proto.ResMachineDataBody
import sirjin.machine.repository.DataModel.MachineMapData
import sirjin.machine.repository._
import sirjin.machine.services.MailAgent

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.duration.DurationInt

object MachineState {
  val typeKey: EntityTypeKey[MachineCommand] = EntityTypeKey[MachineCommand]("MachineAggregate")

  val tags: Seq[String] = Vector.tabulate(20)(i => s"ncId-$i")

  val zoneId           = "Asia/Seoul"
  val resetHour: Int   = sys.env("RESET_HOUR").toInt
  val resetMinute: Int = sys.env("RESET_MINUTE").toInt

  def getDate: String = {
    val zoneDateTime = ZonedDateTime.now(ZoneId.of(MachineState.zoneId))
    if (
      zoneDateTime.getHour < resetHour ||
      (zoneDateTime.getHour <= resetHour && zoneDateTime.getMinute < resetMinute)
    ) {
      zoneDateTime.minusDays(1).format(DateTimeFormatter.ofPattern("YYYY-MM-dd"))
    } else {
      zoneDateTime.format(DateTimeFormatter.ofPattern("YYYY-MM-dd"))
    }
  }
}

class MachineState(
    ctx: PostgresAsyncContext[SnakeCase],
    machineMapRepo: MachineMapDataRepository
) {
  import MachineCommand._
  import MachineEvent._

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def empty: State = State()

  def init(system: ActorSystem[_]): ActorRef[ShardingEnvelope[MachineCommand]] = {
    val behaviorFactory: EntityContext[MachineCommand] => Behavior[MachineCommand] = { entityContext =>
      val i           = math.abs(entityContext.entityId.hashCode % MachineState.tags.size)
      val selectedTag = MachineState.tags(i)
      apply(entityContext.entityId, selectedTag)
    }

    ClusterSharding(system).init(Entity(MachineState.typeKey)(behaviorFactory))
  }

  def apply(ncId: String, projectionTag: String): Behavior[MachineCommand] = {
    EventSourcedBehavior
      .withEnforcedReplies[MachineCommand, MachineEvent, State](
        persistenceId = PersistenceId(MachineState.typeKey.name, ncId),
        emptyState = empty,
        commandHandler = (state, command) => handleCommand(state, command),
        eventHandler = (state, event) => handleEvent(state, event)
      )
      .withTagger(_ => Set(projectionTag))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 1000))
  }

  def handleCommand(
      state: State,
      cmd: MachineCommand
  ): ReplyEffect[MachineEvent, State] = {
    cmd match {
      case ResetEntity(state, replyTo) =>
        if (
          ZonedDateTime.now(ZoneId.of(MachineState.zoneId)).getHour == MachineState.resetHour
          && ZonedDateTime.now(ZoneId.of(MachineState.zoneId)).getMinute == MachineState.resetMinute
          && ZonedDateTime.now(ZoneId.of(MachineState.zoneId)).getSecond < 10
        ) { // reset entity
          logger.info("RESET ENTITY: {}", state)
          val machineDataUpdated = MachineDataUpdated(
            shopId = state.shopId,
            ncId = state.ncId,
            mainPgmNm = state.mainPgmNm,
            lastStatusTime = state.lastStatusTime,
            alarms = state.alarms,
            partNumber = state.partNumber,
            totalPartCount = state.totalPartCount,
            timestamp = System.currentTimeMillis(),
            toolNumber = state.toolNumber,
            cycleStartTime = state.cycleStartTime,
          )

          val summaryDataUpdated = SummaryDataUpdated(
            shopId = state.shopId,
            ncId = state.ncId,
            quantity = 0,
            cycleTime = 0,
            inCycleTime = 0,
            waitTime = 0,
            alarmTime = 0,
            noconnTime = 0,
            opRate = 0f
          )

          Effect.persist(Seq(machineDataUpdated, summaryDataUpdated)).thenNoReply()
        } else {
          Effect.none.thenReply(replyTo)(_ => "success")
        }
      case MakeTimeChart(state, _) =>
        println("################## it's time to add data to [Time Chart Data] ##################")
        val date = MachineState.getDate
        val timeChartEvent = TimeChartDataUpdated(
          shopId = state.shopId,
          ncId = state.ncId,
          datetime = LocalDate.parse(date),
          status = state.lastStatus,
          regdt = LocalDateTime.parse(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)),
          pgmNm = state.mainPgmNm,
          spdLd = state.spdLd,
        )

        val summaryDataEvent = SummaryDataUpdated(
          shopId = state.shopId,
          ncId = state.ncId,
          quantity = state.quantity,
          cycleTime = state.nextMachineOperationTime().cuttingTime,
          inCycleTime = state.nextMachineOperationTime().inCycleTime,
          waitTime = state.nextMachineOperationTime().waitTime,
          alarmTime = state.nextMachineOperationTime().alarmTime,
          noconnTime = state.nextMachineOperationTime().noConnectionTime,
          opRate = state.opRate
        )

        Effect.persist(Seq(timeChartEvent, summaryDataEvent)).thenNoReply()
      case NoConnCheck(state, replyTo) =>
        if ((System.currentTimeMillis() - state.timestamp) / 1000 >= 30) { // no-connection 체크
          logger.info("################## NO-CONNECTION : {} ##################", state.ncId)

          val noConnEvent = NoConnection(
            ncId = state.ncId,
            status = MachineStatus.NO_CONNECTION,
            lastStatus = MachineStatus.NO_CONNECTION,
            timestamp = System.currentTimeMillis()
          )

          Effect.persist(Seq(noConnEvent)).thenReply(replyTo)(_ => "success")
        } else { // spindle load 누적
          val zoneDateTime = ZonedDateTime.now(ZoneId.of(MachineState.zoneId))

          // 가동율 계산
          val startTime = LocalDateTime
            .parse(zoneDateTime.format(DateTimeFormatter.ofPattern("YYYY-MM-dd")) + "T08:00:00")
            .atZone(ZoneId.of(MachineState.zoneId))
            .toEpochSecond * 1000
          val operationRatio = (state.cuttingTime.toFloat / ((System.currentTimeMillis() - startTime) / 1000)) * 100

          val operatingRateEvent = OperatingRateUpdated(
            ncId = state.ncId,
            opRate = operationRatio
          )

          machineMapRepo.update(
            MachineMapData(
              shopId = state.shopId,
              ncId = UUID.fromString(state.ncId),
              opRate = state.opRate,
              quantity = state.quantity,
              pgmNm = state.mainPgmNm,
              status = state.status,
              inCycleTime = state.inCycleTime,
              waitTime = state.waitTime,
              alarmTime = state.alarmTime,
              noconnTime = state.noConnectionTime,
              cycleTime = state.cuttingTime
            )
          )

          Effect.persist(operatingRateEvent).thenReply(replyTo)(_ => "success")
        }
      case command @ UpdateMachineData(data, replyTo) =>
        logger.info("UpdateMachineData : {}", data)

        val currentStatus = command.resolvedStatus
        val _lastStatus = {
          if (State.statusOrderMap(currentStatus) >= State.statusOrderMap(state.lastStatus)) {
            currentStatus
          } else {
            state.lastStatus
          }
        }

        val currentTime = System.currentTimeMillis()

        val event = MachineDataUpdated(
          shopId = data.shopId.toString,
          ncId = data.ncId,
          opRate = state.opRate,
          mainPgmNm = data.mainPgmNm,
          path = 1,
          status = currentStatus,
          lastStatus = _lastStatus,
          lastStatusTime = state.lastStatusTime,
          alarms = data.alarms,
          totalPartCount = data.totalPartCount,
          timestamp = currentTime,
          toolNumber = data.pathData.head.auxCodes.head.t,
          spdLd = data.pathData.head.spindleLoad,
          cycleStartTime = state.cycleStartTime,
          partNumber = state.partNumber,
        )
        Effect
          .persist(Seq(event))
          .thenReply(replyTo)(_ => ResBody(rtnMsg = ReturnMessage.SUCCESS))

      case GetCurrentMachineInfo(ncId, replyTo) =>
        logger.info("GetCurrentMachineInfo : {}", ncId)
        Effect.reply(replyTo)(
          ResMachineDataBody(
            ncId = ncId,
            inCycleTime = state.inCycleTime,
            waitTime = state.waitTime,
            alarmTime = state.alarmTime,
            noConnectionTime = state.noConnectionTime,
            status = state.status,
            lastStatus = state.status,
            lastStatusTime = state.lastStatusTime,
            alarms = state.alarms,
            partNumber = state.partNumber.getOrElse(""),
            cuttingTime = state.cuttingTime,
            opRate = state.opRate,
            partCount = state.partCount,
            totalPartCount = state.totalPartCount,
            timestamp = state.timestamp,
            toolNumber = state.toolNumber,
            quantity = state.quantity
          )
        )
      case GetCurrentMachineState(replyTo) =>
        Effect.reply(replyTo)(state)
      case UpdateOperationState(data, replyTo) =>
        val operatingEvent = OperatingDataUpdated(
          orderNum = data.orderNum,
          partNum = data.partNum,
          ncType = data.ncType,
          isLast = data.isLast,
          eventType = data.eventType,
          name = data.name,
          programStartTime = LocalDateTime.parse(data.programStartTime)
        )

        Effect.persist(operatingEvent).thenReply(replyTo)(_ => ResBody(rtnMsg = ReturnMessage.SUCCESS))

      case UpdateProgramStatus(programEvent, replyTo) =>
        Effect.persist(programEvent).thenReply(replyTo)(_ => ResBody(rtnMsg = ReturnMessage.SUCCESS))

      case UpdatePartCount(data, eventType, replyTo) =>
        val productionQuantity =
          if (eventType == PartCountEventType.AUTO) { // raw data 처리
            state.quantity + (data.totalPartCount - state.totalPartCount)
          } else if (eventType == PartCountEventType.MANUAL) { // 모바일 api (IO 장비 일 때)
            state.quantity + 1
          } else {
            state.quantity
          }

        val partCountEvent = PartCountUpdated(
          ncId = state.ncId,
          datetime = LocalDateTime.parse(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)),
          shopId = state.shopId,
          quantity = if (eventType == PartCountEventType.AUTO) data.totalPartCount - state.totalPartCount else 1,
          totalQuantity = data.totalPartCount,
          dailyPartCount = productionQuantity,
          orderNumber = data.orderNumber,
          productNumber = data.productNumber,
          model = data.model,
          program = data.program,
          programStartTime = data.programStartTime,
          programEndTime = data.programEndTime,
          last = data.last
        )

        Effect.persist(partCountEvent).thenReply(replyTo)(_ => ResBody(rtnMsg = ReturnMessage.SUCCESS))

      case GetAlarmData(replyTo) =>
        Effect.reply(replyTo)(state.alarms)
      case UpdateAlarmState(raw, alarm, alarmTy, replyTo) =>
        val alarmEvent = AlarmDataUpdated(
          shopId = raw.shopId.toString,
          ncId = raw.ncId,
          alarms = alarm,
          alarmTy = alarmTy
        )

        Effect.persist(alarmEvent).thenReply(replyTo)(_ => ResBody(rtnMsg = ReturnMessage.SUCCESS))
    }
  }

  def handleEvent(state: State, evt: MachineEvent): State = evt match {
    case event: MachineDataUpdated =>
      logger.info("MachineDataUpdated : {}", event)
      state.copy(
        shopId = event.shopId,
        ncId = event.ncId,
        status = event.status,
        lastStatus = event.lastStatus,
        lastStatusTime = event.lastStatusTime,
        mainPgmNm = event.mainPgmNm,
        alarms = event.alarms,
        partNumber = event.partNumber,
        opRate = event.opRate,
        timestamp = event.timestamp,
        toolNumber = event.toolNumber,
        spdLd = event.spdLd,
        cycleStartTime = event.cycleStartTime,
      )
    case event: AlarmDataUpdated =>
      logger.info("AlarmDataUpdated : {}", event)
      state.copy(alarms = event.alarms)
    case event: ProgramUpdated =>
      logger.info("ProgramUpdated : {}", event)
      state
    case event: PartCountUpdated =>
      // logger.info("PartCountUpdated : {}", event)
      state.copy(
        quantity = event.dailyPartCount,
        partCount = event.dailyPartCount,
        totalPartCount = event.totalQuantity
      )
    case event: TimeChartDataUpdated =>
      logger.info("TimeChartDataUpdated : {}", event)
      state.copy(
        lastStatus = state.status,
      )
    case event: SummaryDataUpdated =>
      logger.info("SummaryDataUpdated : {}", event)
      state.copy(
        cuttingTime = event.cycleTime,
        inCycleTime = event.inCycleTime,
        waitTime = event.waitTime,
        alarmTime = event.alarmTime,
        noConnectionTime = event.noconnTime
      )
    case event: OperatingDataUpdated =>
      // logger.info("UpdateOperationState : {}", event)
      state.copy(operateData =
        OperatingData(
          orderNum = event.orderNum,
          partNum = event.partNum,
          ncType = event.ncType,
          isLast = event.isLast,
          eventType = event.eventType,
          name = event.name,
          programStartTime = event.programStartTime.toString
        )
      )
    case event: NoConnection =>
      logger.info("NoConnection : {}", event)
      state.copy(
        status = event.status,
        lastStatus = event.lastStatus,
        timestamp = event.timestamp
      )
    case event: OperatingRateUpdated =>
      // logger.info("OperatingRateUpdated : {}", event)
      state.copy(
        opRate = event.opRate,
      )
  }

  def sendErrorMailToAdmin(message: String): Unit = {
    new MailAgent(
      from = "jay.kim@elfinos.io",
      to = List("jay.kim@elfinos.io"),
      cc = List("jay.kim@elfinos.io"),
      bcc = List("jay.kim@elfinos.io"),
      subject = "SnapShot RecoveryFailed",
      content = message,
      smtpHost = "elfinos.io",
      pwd = "SFZM8VUQOawLdlnBCWIsYfFE",
    ).sendMessage
  }
}
