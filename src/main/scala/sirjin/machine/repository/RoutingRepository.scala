package sirjin.machine.repository

import scala.concurrent.Future

trait RoutingRepository {
  import sirjin.machine.repository.DataModel._

  def select(params: Routing): Future[Option[(Routing, MachineList)]]
}
