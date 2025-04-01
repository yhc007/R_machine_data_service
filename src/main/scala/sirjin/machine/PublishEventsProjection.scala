package sirjin.machine

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.SendProducer
import akka.persistence.cassandra.query.javadsl.CassandraReadJournal
import akka.persistence.query.Offset
import akka.projection.ProjectionBehavior
import akka.projection.ProjectionId
import akka.projection.cassandra.scaladsl.CassandraProjection
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.AtLeastOnceProjection
import akka.projection.scaladsl.SourceProvider
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer

object PublishEventsProjection {

  def init(system: ActorSystem[_]): Unit = {
    val sendProducer = createProducer(system)
    val topic =
      system.settings.config.getString("machine-data-service.kafka.topic")

    ShardedDaemonProcess(system).init(
      name = "PublishEventsProjection",
      MachineState.tags.size,
      index => ProjectionBehavior(createProjectionFor(system, topic, sendProducer, index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop)
    )
  }

  private def createProducer(system: ActorSystem[_]): SendProducer[String, Array[Byte]] = {
    val producerSettings =
      ProducerSettings(system, new StringSerializer, new ByteArraySerializer)
    val sendProducer =
      SendProducer(producerSettings)(system)
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "close-sendProducer") {
      () =>
        sendProducer.close()
    }
    sendProducer
  }

  private def createProjectionFor(
      system: ActorSystem[_],
      topic: String,
      sendProducer: SendProducer[String, Array[Byte]],
      index: Int
  ): AtLeastOnceProjection[Offset, EventEnvelope[MachineEvent]] = {
    val tag = MachineState.tags(index)
    val sourceProvider: SourceProvider[Offset, EventEnvelope[MachineEvent]] =
      EventSourcedProvider
        .eventsByTag[MachineEvent](system = system, readJournalPluginId = CassandraReadJournal.Identifier, tag = tag)

    CassandraProjection.atLeastOnce(
      projectionId = ProjectionId("PublishEventsProjection", tag),
      sourceProvider = sourceProvider,
      handler = () => new PublishEventsProjectionHandler(system, topic, sendProducer),
    )
  }
}
