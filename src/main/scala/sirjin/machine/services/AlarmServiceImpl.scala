package sirjin.machine.services

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.util.Timeout
import org.slf4j.LoggerFactory
import sirjin.machine.MachineCommand.GetAlarmData
import sirjin.machine.MachineCommand.UpdateAlarmState
import sirjin.machine.repository.AlarmTy
import sirjin.machine.repository.HttpDataModel.MachineRawData
import sirjin.machine.MachineCommand
import sirjin.machine.MachineState
import sirjin.machine.proto

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class AlarmServiceImpl(system: ActorSystem[_]) extends AlarmService {
  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout     = Timeout(5.seconds)
  private val logger                = LoggerFactory.getLogger(getClass)

  def entityRef(id: String): EntityRef[MachineCommand] = {
    val key = s"$id"

    val sharding = ClusterSharding(system)
    sharding.entityRefFor(MachineState.typeKey, key)
  }

  override def compareWithPrevAlarm(ncId: String, raw: MachineRawData): Future[Unit] = {
    for {
      existAlarm <- entityRef(ncId).ask(replyTo => GetAlarmData(replyTo))
      oldAlarmCodeSet      = existAlarm.map(_.alarmCode).toSet
      newAlarmCodeSet      = raw.alarms.map(_.alarmCode).toSet
      resolvedAlarmCodeSet = oldAlarmCodeSet.diff(newAlarmCodeSet)
      addedAlarmCodeSet    = newAlarmCodeSet.diff(oldAlarmCodeSet)
      resolvedAlarms = {
        for {
          alarm <- existAlarm if resolvedAlarmCodeSet.contains(alarm.alarmCode)
        } yield alarm
      }
      addedAlarms = {
        for {
          alarm <- raw.alarms if addedAlarmCodeSet.contains(alarm.alarmCode)
        } yield alarm
      }
      _ <- {
        if (resolvedAlarms.nonEmpty) {
          logger.info("[알람 해제] : {}", resolvedAlarms)
          entityRef(ncId).ask(replyTo => UpdateAlarmState(raw, resolvedAlarms, AlarmTy.FIXED, replyTo))
        }

        if (addedAlarms.nonEmpty) {
          logger.info("[알람 발생] : {}", addedAlarms)
          entityRef(ncId).ask(replyTo =>
            UpdateAlarmState(
              raw,
              addedAlarms.map(v =>
                proto.AlarmData(
                  `type` = v.`type`,
                  alarmCode = v.alarmCode,
                  alarmMessage = v.alarmMessage
                )
              ),
              AlarmTy.OCCURRED,
              replyTo
            )
          )
        } else Future.unit
      }
    } yield ()
  }
}
