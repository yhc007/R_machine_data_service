package sirjin.machine.services

import sirjin.machine.repository.DataModel.Routing
import sirjin.machine.repository.DataModel
import sirjin.machine.repository.RoutingRepository

import java.util.UUID
import scala.concurrent.Future

class RoutingServiceImpl(routingRepo: RoutingRepository) extends RoutingService {
  override def checkExistRouting(
      ncId: String,
      mainPgmNm: String
  ): Future[Option[(DataModel.Routing, DataModel.MachineList)]] = {
    routingRepo
      .select(
        Routing(
          ncId = UUID.fromString(ncId),
          program = mainPgmNm,
        )
      )
  }
}
