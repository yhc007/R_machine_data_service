package task

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.util.Timeout
import sirjin.machine.MachineCommand.GetCurrentMachineState
import sirjin.machine.MachineCommand.MakeTimeChart
import sirjin.machine.repository.MachineMapDataRepository
import sirjin.machine.MachineCommand
import sirjin.machine.MachineState

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class TimeChartMaker(machineMapDataRepository: MachineMapDataRepository, system: ActorSystem[_]) extends Runnable {
  implicit val timeout: Timeout = Timeout(5.seconds)
  def entityRef(id: String): EntityRef[MachineCommand] = {
    val key = s"$id"

    val sharding = ClusterSharding(system)
    sharding.entityRefFor(MachineState.typeKey, key)
  }

  override def run(): Unit = {
    for {
      machines <- machineMapDataRepository.findAll()
    } yield machines.map(machine => {
      for {
        state <- entityRef(machine.ncId.toString).ask(replyTo => GetCurrentMachineState(replyTo))
        _     <- entityRef(machine.ncId.toString).ask(replyTo => MakeTimeChart(state, replyTo))
      } yield ()
    })
  }
}
