package sirjin.machine

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.util.Timeout
import com.google.protobuf.empty.Empty
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sirjin.machine.proto.RawData
import sirjin.machine.proto.ReqMachineDataBody
import sirjin.machine.proto.ResBody
import sirjin.machine.proto.ResMachineDataBody
import sirjin.machine.repository._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class MachineServiceImpl(
    system: ActorSystem[_],
) extends proto.MachineService {
  import MachineCommand._

  implicit val timeout: Timeout = Timeout(5.seconds)
  val logger: Logger            = LoggerFactory.getLogger(getClass)

  def entityRef(id: String): EntityRef[MachineCommand] = {
//    val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
//    val key  = s"$id-$date"
    val key = s"$id"

    val sharding = ClusterSharding(system)
    sharding.entityRefFor(MachineState.typeKey, key)
  }

  override def addRawData(in: RawData): Future[ResBody] = {
    logger.info("addRawData {}", in)
    entityRef(in.ncId).ask(replyTo => UpdateMachineData(in, replyTo))
  }

  override def getCurrentMachineInfo(in: ReqMachineDataBody): Future[ResMachineDataBody] = {
    logger.info("getCurrentMachineInfo {}", in)
    entityRef(in.ncId).ask(replyTo => GetCurrentMachineInfo(in.ncId, replyTo))
  }

  override def healthCheck(in: Empty): Future[ResBody] =
    Future.successful(ResBody(rtnMsg = ReturnMessage.SUCCESS))
}
