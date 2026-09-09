package lib.util

import chisel3._
import chisel3.util._

object ResetSynchronizer {
  def apply(input: AsyncReset): Bool = {
    val sync = Module(new ResetSynchronizer)
    sync.io.input := input
    sync.io.output
  }
}

/**
 * Synchronizes an async, active-high reset signal into a synchronous active-high reset.
 */
class ResetSynchronizer(stages: Int = 2) extends Module {
  val io = IO(new Bundle {
    val input = Input(AsyncReset())
    val output = Output(Bool())
  })
  io.output := withReset (io.input) {
    ShiftRegister(false.B, stages + 1, true.B, true.B)
  }
}