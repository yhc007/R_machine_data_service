package sirjin.machine.repository

import io.getquill.PostgresAsyncContext
import io.getquill.SnakeCase
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class MachineMapDataRepositoryImpl(ctx: PostgresAsyncContext[SnakeCase]) extends MachineMapDataRepository {
  import ctx._
  import sirjin.machine.repository.DataModel._

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val logger: Logger = LoggerFactory.getLogger(getClass)
  override def update(params: MachineMapData): Future[Unit] = {
    val q = quote {
      query[MachineMapData]
        .insert(lift(params))
        .onConflictUpdate(_.ncId, _.shopId)(
          (t, e) => t.opRate -> e.opRate,
          (t, e) => t.quantity -> e.quantity,
          (t, e) => t.pgmNm -> e.pgmNm,
          (t, e) => t.status -> e.status,
          (t, e) => t.inCycleTime -> e.inCycleTime,
          (t, e) => t.waitTime -> e.waitTime,
          (t, e) => t.alarmTime -> e.alarmTime,
          (t, e) => t.noconnTime -> e.noconnTime,
          (t, e) => t.cycleTime -> e.cycleTime,
        )
    }

    for {
      _ <- ctx.run(q)
    } yield ()
  }

  override def updateOperating(params: MachineMapData): Future[Unit] = {
    // logger.info("UPDATE TO MachineMapData [updateOperating] : {}", params)

    val q = quote {
      query[MachineMapData]
        .filter(_.shopId == lift(params.shopId))
        .filter(_.ncId == lift(params.ncId))
        .update(
          _.productNumber -> lift(params.productNumber),
          _.orderNumber   -> lift(params.orderNumber),
          _.isLast        -> lift(params.isLast)
        )
    }

    for {
      _ <- ctx.run(q)
    } yield ()
  }

  override def findAll(): Future[Seq[MachineMapData]] = {
    for {
      _ <- Future.unit
      q = quote {
        query[MachineMapData]
      }
      machineList <- ctx.run(q)
    } yield machineList
  }
}
