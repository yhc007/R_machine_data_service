package sirjin.machine.repository

import scala.concurrent.Future

trait MachineMapDataRepository {
  import sirjin.machine.repository.DataModel._

  def update(params: MachineMapData): Future[Unit]
  def updateOperating(params: MachineMapData): Future[Unit]
  def findAll(): Future[Seq[MachineMapData]]
}
