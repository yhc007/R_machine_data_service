package sirjin.machine.services

import sirjin.machine.repository.HttpDataModel.MachineRawData

import scala.concurrent.Future

trait AlarmService {
  def compareWithPrevAlarm(ncId: String, raw: MachineRawData): Future[Unit]
}
