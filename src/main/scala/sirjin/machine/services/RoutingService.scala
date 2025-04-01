package sirjin.machine.services

import sirjin.machine.repository.DataModel.MachineList
import sirjin.machine.repository.DataModel.Routing

import scala.concurrent.Future

trait RoutingService {
  def checkExistRouting(ncId: String, mainPgmNm: String): Future[Option[(Routing, MachineList)]]
}
