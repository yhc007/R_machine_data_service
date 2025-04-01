package sirjin.machine.repository

import io.getquill.PostgresAsyncContext
import io.getquill.SnakeCase
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.concurrent.Future

class RoutingRepositoryImpl(ctx: PostgresAsyncContext[SnakeCase]) extends RoutingRepository {
  import ctx._
  import sirjin.machine.repository.DataModel._

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val logger: Logger = LoggerFactory.getLogger(getClass)
  override def select(params: Routing): Future[Option[(Routing, MachineList)]] = {
    // logger.info("SELECT FROM Routing : {}", params)

    val q = quote {
      query[Routing]
        .filter(_.ncId == lift(params.ncId))
        .filter(_.program == lift(params.program))
        .join(query[MachineList])
        .on(_.ncId == _.ncId)
    }

    for {
      routing <- ctx.run(q)
    } yield routing.headOption
  }
}
