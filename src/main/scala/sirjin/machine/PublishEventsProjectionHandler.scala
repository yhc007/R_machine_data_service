package sirjin.machine

import akka.Done
import akka.actor.typed.ActorSystem
import akka.kafka.scaladsl.SendProducer
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import com.google.protobuf.any.{ Any => ScalaPBAny }
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sirjin.machine.proto.AlarmDataSet

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class PublishEventsProjectionHandler(
    system: ActorSystem[_],
    topic: String,
    sendProducer: SendProducer[String, Array[Byte]]
) extends Handler[EventEnvelope[MachineEvent]] {
  import MachineEvent._

  val logger: Logger = LoggerFactory.getLogger(getClass)

  private implicit val ec: ExecutionContext = system.executionContext

  override def process(envelope: EventEnvelope[MachineEvent]): Future[Done] = {
    val event = envelope.event

    val key            = event.ncId
    val producerRecord = new ProducerRecord(topic, key, serialize(event))
    val result = sendProducer.send(producerRecord).map { recordMetadata =>
      logger.info("Published event [{}] to topic/partition {}/{}", event, topic, recordMetadata.partition)
      Done
    }
    result
  }

  private def serialize(event: MachineEvent): Array[Byte] = {
    val protoMessage = event match {
      case event: MachineDataUpdated =>
        proto.MachineDataUpdated(
          shopId = event.shopId,
          ncId = event.ncId,
          cuttingTime = event.cuttingTime,
          inCycleTime = event.inCycleTime,
          waitTime = event.waitTime,
          alarmTime = event.alarmTime,
          noConnectionTime = event.noConnectionTime,
          status = event.status,
          partNumber = event.partNumber.getOrElse("-1"),
          opRate = event.opRate,
          path = event.path,
          partCount = event.partCount,
          totalPartCount = event.totalPartCount,
          timestamp = event.timestamp,
          toolNumber = event.toolNumber,
          mainPgmNm = event.mainPgmNm,
          date = MachineState.getDate,
          spdLd = event.spdLd
        )

      case event: SummaryDataUpdated =>
        proto.SummaryDataUpdated(
          shopId = event.shopId,
          date = MachineState.getDate,
          ncId = event.ncId,
          quantity = event.quantity,
          cycleTime = event.cycleTime,
          inCycleTime = event.inCycleTime,
          waitTime = event.waitTime,
          alarmTime = event.alarmTime,
          noconnTime = event.noconnTime,
          opRate = event.opRate,
        )

      case event: AlarmDataUpdated =>
        proto.AlarmDataUpdated(
          shopId = event.shopId,
          ncId = event.ncId,
          alarmTy = event.alarmTy,
          alarmData = event.alarms.map(v => AlarmDataSet(alarmCode = v.alarmCode, alarmMessage = v.alarmMessage))
        )

      case event: ProgramUpdated =>
        proto.ProgramUpdated(
          shopId = event.shopId,
          ncId = event.ncId,
          prevProgram = event.prevProgram,
          currentProgram = event.currentProgram,
          programStartTime = event.programStartTime.toString,
          programEndTime = event.programEndTime.toString,
          eventType = event.eventType,
          orderNumber = event.orderNumber,
          productNumber = event.productNumber,
          model = event.model,
          last = event.last,
        )

      case event: PartCountUpdated =>
        proto.PartCountUpdated(
          datetime = event.datetime.toString,
          ncId = event.ncId,
          shopId = event.shopId,
          quantity = event.quantity,
          orderNumber = event.orderNumber,
          productNumber = event.productNumber,
          model = event.model,
          program = event.program,
          programStartTime = event.programStartTime.toString,
          programEndTime = event.programEndTime.toString,
          last = event.last,
        )

      case event: TimeChartDataUpdated =>
        proto.TimeChartDataUpdated(
          shopId = event.shopId,
          ncId = event.ncId,
          datetime = event.datetime.toString,
          status = event.status,
          regdt = event.regdt.toString,
          pgmNm = event.pgmNm,
          spdLd = event.spdLd,
        )
      case _ => proto.AuxCode(t = "0")
    }
    // pack in Any so that type information is included for deserialization
    ScalaPBAny.pack(protoMessage, "machine-data-service").toByteArray
  }
}
