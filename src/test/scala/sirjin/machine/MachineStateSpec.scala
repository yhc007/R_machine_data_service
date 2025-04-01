package sirjin.machine

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.softwaremill.macwire.wire
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.getquill.PostgresAsyncContext
import io.getquill.SnakeCase
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike
import sirjin.machine.MachineCommand.UpdateMachineData
import sirjin.machine.MachineEvent.MachineDataUpdated
import sirjin.machine.proto.AlarmData
import sirjin.machine.proto.AuxCode
import sirjin.machine.proto.PathData
import sirjin.machine.proto.RawData
import sirjin.machine.proto.ResBody
import sirjin.machine.repository.MachineMapDataRepository
import sirjin.machine.repository.MachineMapDataRepositoryImpl
import sirjin.machine.repository.ReturnMessage

object MachineStateSpec {
  val config = ConfigFactory
    .parseString("""
      akka {
        actor {
          serialization-bindings {
            "sirjin.machine.CborSerializable" = jackson-cbor
            "com.google.protobuf.Message" = proto
          }
          allow-java-serialization = on
          warn-about-java-serializer-usage = off
        }
        persistence {
          journal {
            plugin = "akka.persistence.journal.inmem"
          }
          snapshot-store.plugin = "akka.persistence.snapshot-store.local"
        }
        persistence.testkit {
          events-by-persistence-id {
            max-buffer-entries = 10000
          }
        }
      }
      """)
    .withFallback(EventSourcedBehaviorTestKit.config)
}
class MachineStateSpec
    extends ScalaTestWithActorTestKit(MachineStateSpec.config)
    with AnyWordSpecLike
    with BeforeAndAfterEach {

  private val ncId                                  = "1cab6abf-0056-4838-a1f1-251c1691ffca"
  lazy val ctx: PostgresAsyncContext[SnakeCase]     = new PostgresAsyncContext(SnakeCase, "ctx")
  lazy val machineMapRepo: MachineMapDataRepository = wire[MachineMapDataRepositoryImpl]

  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[MachineCommand, MachineEvent, State](
      system,
      new MachineState(ctx, machineMapRepo).apply(ncId, MachineState.tags.head)
    )

  protected override def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "The Machine Status" should {
    "update machine status" in {
      val result1 =
        eventSourcedTestKit.runCommand[ResBody](replyTo =>
          UpdateMachineData(
            RawData(
              shopId = 1,
              ncId = ncId,
              timestamp = 164419,
              partCount = 10,
              totalPartCount = 20,
              mainPgmNm = "pgm",
              status = "START",
              mode = "AUTO",
              pathData = Seq(
                PathData(
                  path = "1",
                  spindleLoad = 10,
                  spindleOverride = 10,
                  spindleSpeed = 10,
                  feedOverride = 10,
                  auxCodes = Some(AuxCode(t = "T1")),
                )
              ),
              alarms = Seq.empty,
            ),
            replyTo
          )
        )
      result1.reply should ===(ResBody(rtnMsg = ReturnMessage.SUCCESS))
      
      result1.hasNoEvents shouldBe false
      
      result1.event should ===(
        MachineDataUpdated(
          shopId = "1",
          ncId = ncId,
          cuttingTime = 0,
          inCycleTime = 0,
          waitTime = 0,
          alarmTime = 0,
          noConnectionTime = 0,
          status = "CUTTING",
          mainPgmNm = "pgm",
          lastStatus = "CUTTING",
          lastStatusTime = result1.event.asInstanceOf[MachineDataUpdated].lastStatusTime,
          alarms = Seq.empty,
          partNumber = None,
          opRate = 0.0f,
          path = 1,
          partCount = 10,
          totalPartCount = 20,
          timestamp = result1.event.asInstanceOf[MachineDataUpdated].timestamp,
          toolNumber = "T1",
          spdLd = 10.0f,
          cycleStartTime = result1.event.asInstanceOf[MachineDataUpdated].cycleStartTime,
          startOperating = false
        )
      )
    }
  }
}
