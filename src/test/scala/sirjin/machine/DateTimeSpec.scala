package sirjin.machine

import org.scalatest.funsuite.AnyFunSuite

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class DateTimeSpec extends AnyFunSuite {
  test("") {
    val now = LocalDateTime.now()
    val start = LocalDateTime.of(now.toLocalDate, LocalTime.of(8, 0))
    val cuttingTimeInSeconds = 3600
    val seconds = Duration.between(start, now).toSeconds
    val ratio = cuttingTimeInSeconds.toDouble / seconds
    assert(ratio < .25d)
  }
}
