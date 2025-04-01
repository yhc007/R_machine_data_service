package sirjin.machine.services

import sirjin.machine.State
import sirjin.machine.repository.HttpDataModel.MachineRawData

import scala.concurrent.Future

object ProgramService {}

trait ProgramService {
  def compareWithPevProgram(raw: MachineRawData, state: State): Future[Unit]
}
