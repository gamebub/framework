package lib.util

import chisel3._

/// This class takes a bitvector / struct of 1-bit active-high buttons,
/// and an "enable" flag.
///
/// Button presses and release only take effect if "enable" is high
///  * presses are edge based
///  * releases are level based
///
/// This gives reasonable behavior when pausing and navigating through
/// an external menu, and then resuming and maintaining a reasonable button state.
class ButtonFilter[T <: Data](gen: T) extends Module {
  val io = IO(new Bundle {
    val enable = Input(Bool())
    val input = Input(gen)
    val output = Output(gen)
  })

  val state = RegInit(0.U(gen.getWidth.W))
  io.output := state.asTypeOf(gen)

  val input = io.input.asUInt
  val prevInput = RegNext(input)
  when (io.enable) {
    state := (state | (input & (~prevInput).asUInt)) & input.asUInt
  }
}
