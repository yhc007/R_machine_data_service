package sirjin.machine

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import com.softwaremill.macwire._
import io.getquill.PostgresAsyncContext
import io.getquill.SnakeCase
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sirjin.machine.repository.MachineMapDataRepository
import sirjin.machine.repository.MachineMapDataRepositoryImpl
import sirjin.machine.repository.RoutingRepository
import sirjin.machine.repository.RoutingRepositoryImpl
import sirjin.machine.services._
import task.CheckEntityStatus
import task.TimeChartMaker

import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

object Main {

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    val system = ActorSystem[Nothing](Behaviors.empty, "sirjin-data-service")

    try {
      init(system)
    } catch {
      case NonFatal(e) =>
        logger.error("Terminating due to initialization failure.", e)
        system.terminate()
    }
  }

  def init(system: ActorSystem[_]): Unit = {
    lazy val ctx: PostgresAsyncContext[SnakeCase]     = new PostgresAsyncContext(SnakeCase, "ctx")
    lazy val machineMapRepo: MachineMapDataRepository = wire[MachineMapDataRepositoryImpl]
    lazy val routingRepo: RoutingRepository           = wire[RoutingRepositoryImpl]
    lazy val routingService: RoutingService           = wire[RoutingServiceImpl]
    lazy val programService: ProgramService           = wire[ProgramServiceImpl]
    lazy val alarmService: AlarmService               = wire[AlarmServiceImpl]

    system.scheduler.scheduleAtFixedRate(initialDelay = 5.seconds, interval = 120.seconds) { wire[TimeChartMaker] }(
      scala.concurrent.ExecutionContext.Implicits.global
    )

    system.scheduler.scheduleAtFixedRate(initialDelay = 5.seconds, interval = 5.seconds) { wire[CheckEntityStatus] }(
      scala.concurrent.ExecutionContext.Implicits.global
    )

    AkkaManagement(system).start()
    ClusterBootstrap(system).start()

    wire[MachineState].init(system)
    PublishEventsProjection.init(system)

    val config = system.settings.config

    val grpcInterface                     = config.getString("sirjin-data-service.grpc.interface")
    val grpcPort                          = config.getInt("sirjin-data-service.grpc.port")
    val grpcService: proto.MachineService = wire[MachineServiceImpl]

    MachineServer.start(grpcInterface, grpcPort, system, grpcService)
    wire[MachineHttpServer].start(grpcInterface, 8080, system)
  }

}
