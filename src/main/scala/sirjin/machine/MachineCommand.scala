package sirjin.machine

import akka.actor.typed.ActorRef
import sirjin.machine.MachineEvent.ProgramUpdated
import sirjin.machine.State.OperatingData
import sirjin.machine.State.PartCountData
import sirjin.machine.proto.AlarmData
import sirjin.machine.proto.RawData
import sirjin.machine.proto.ResBody
import sirjin.machine.proto.ResMachineDataBody
import sirjin.machine.repository.HttpDataModel.MachineRawData
import sirjin.machine.repository.MachineStatus
sealed trait MachineCommand extends CborSerializable

object MachineCommand {
  case class MakeTimeChart(state: State, replyTo: ActorRef[String]) extends MachineCommand
  case class NoConnCheck(state: State, replyTo: ActorRef[String])   extends MachineCommand
  case class ResetEntity(state: State, replyTo: ActorRef[String])   extends MachineCommand
  case class UpdateMachineData(data: RawData, replyTo: ActorRef[ResBody]) extends MachineCommand {
    def resolvedStatus: String = {
      if (data.alarms.isEmpty) {
        if (data.status == "START" && data.pathData.head.spindleLoad > 0) {
          MachineStatus.CUTTING
        } else if (data.status == "START") {
          MachineStatus.IN_CYCLE
        } else {
          MachineStatus.WAIT
        }
      } else {
        MachineStatus.ALARM
      }
    }
  }
  case class GetCurrentMachineInfo(ncId: String, replyTo: ActorRef[ResMachineDataBody])      extends MachineCommand
  case class GetCurrentMachineState(replyTo: ActorRef[State])                                extends MachineCommand
  case class UpdateOperationState(data: OperatingData, replyTo: ActorRef[ResBody])           extends MachineCommand
  case class UpdatePartCount(raw: PartCountData, eventType: String, replyTo: ActorRef[Unit]) extends MachineCommand
  case class UpdateProgramStatus(programEvent: ProgramUpdated, replyTo: ActorRef[ResBody])   extends MachineCommand
  case class GetAlarmData(replyTo: ActorRef[Seq[AlarmData]])                                 extends MachineCommand
  case class UpdateAlarmState(raw: MachineRawData, alarm: Seq[AlarmData], alarmTy: String, replyTo: ActorRef[ResBody])
      extends MachineCommand
}
