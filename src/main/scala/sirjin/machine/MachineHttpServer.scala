package sirjin.machine

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import sirjin.machine.MachineCommand._
import sirjin.machine.repository._
import sirjin.machine.services.AlarmService
import sirjin.machine.services.ProgramService

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

class MachineHttpServer(
    programService: ProgramService,
    alarmService: AlarmService,
    machineMapRepo: MachineMapDataRepository,
) {

  import HttpDataModel._

  implicit def start(interface: String, port: Int, system: ActorSystem[_]): Unit = {
    implicit val sys: ActorSystem[_]  = system
    implicit val ec: ExecutionContext = system.executionContext
    implicit val timeout: Timeout     = Timeout(5.seconds)

    def entityRef(id: String): EntityRef[MachineCommand] = {
      val key = s"$id"

      val sharding = ClusterSharding(system)
      sharding.entityRefFor(MachineState.typeKey, key)
    }

    val route =
      path("health") {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
        }
      } ~
        path("machine" / "raw") {
          post {
            entity(as[MachineRawData]) { raw =>
              complete {
                for {
                  state <- entityRef(raw.ncId).ask(replyTo => GetCurrentMachineState(replyTo))
                  _     <- alarmService.compareWithPrevAlarm(raw.ncId, raw)
                  _     <- programService.compareWithPevProgram(raw, state)
                  _     <- entityRef(raw.ncId).ask(replyTo => UpdateMachineData(raw.convertToProtoDataType, replyTo))
                } yield ReturnMessage.SUCCESS
              }
            }
          }
        } ~
        path("machine" / "state") {
          get {
            parameters("ncId".as[String]) { ncId =>
              complete {
                for {
                  state <- entityRef(ncId).ask(replyTo => GetCurrentMachineState(replyTo))
                } yield {
                  _State(
                    shopId = state.shopId,
                    ncId = state.ncId,
                    inCycleTime = state.inCycleTime,
                    waitTime = state.waitTime,
                    alarmTime = state.alarmTime,
                    noConnectionTime = state.noConnectionTime,
                    status = state.status,
                    mainPgmNm = state.mainPgmNm,
                    lastStatus = state.lastStatus,
                    lastStatusTime = state.lastStatusTime,
                    partNumber = state.partNumber,
                    partCount = state.partCount,
                    quantity = state.quantity,
                    totalPartCount = state.totalPartCount,
                    cuttingTime = state.cuttingTime,
                    opRate = state.opRate,
                    timestamp = state.timestamp,
                    toolNumber = state.toolNumber,
                    spdLd = state.spdLd,
                    cycleStartTime = state.cycleStartTime.toString,
                    operateData = state.operateData,
                    // alarm = state.alarms.map(v => AlarmData(alarmCode = v.alarmCode, alarmMessage = v.alarmMessage))
                  )
                }
              }
            }
          }
        }

    val bound = Http().newServerAt(interface, port).bind(route)

    bound.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("MachineHttpService at Http server {}:{}", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind Http endpoint, terminating system", ex)
        system.terminate()
    }
  }
}
