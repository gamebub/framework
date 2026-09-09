package net.gamebub.framework.interface

import chisel3._

object ClocksV0 {
  /**
   * Get the (minimum, maximum) frequency (Hz) for the display clock for the
   * given frame period.
   */
  var getClockDisplayHz: (Double) => (Int, Int) = (_ => ???)
}

class ClocksV0(
  val clockSystemHz: Int,
  val clockDisplayHz: Int,
  val clockSpiHz: Int,
) extends Bundle {
  /** Input clock, 50 MHz **/
  val clockIn50M = Input(Clock())

  /** Output clock: system (used for interfaces unless otherwise specified) */
  val clockOutSystem = Output(Clock())
  /** Output clock: display (used to drive the display) */
  val clockOutDisplay = Output(Clock())
  /** Output clock: host SPI (must be > 160 MHz) */
  val clockOutSpi = Output(Clock())

  /** Whether the clocks are locked and stable (PLL output signal) */
  val locked = Output(Bool())
}
