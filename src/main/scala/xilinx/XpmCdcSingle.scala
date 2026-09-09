package xilinx

import chisel3._

object XpmCdcSingle {
  /**
   * Synchronize a single bit from a source clock domain to the current clock domain.
   */
  def apply(sourceClock: Clock, sourceInput: Bool): Bool = {
    val cdc = Module(new XpmCdcSingle)
    cdc.io.sourceClock := sourceClock
    cdc.io.sourceInput := sourceInput
    cdc.io.destOutput
  }
}

class XpmCdcSingle extends Module {
  val io = IO(new Bundle {
    /** Source clock */
    val sourceClock = Input(Clock())
    /** Source input bit */
    val sourceInput = Input(Bool())

    /** Destination output bit */
    val destOutput = Output(Bool())
  })

  val cdc = Module(new xpm_cdc_single())
  cdc.io.src_clk := io.sourceClock.asBool
  cdc.io.src_in := io.sourceInput
  cdc.io.dest_clk := clock.asBool
  io.destOutput := cdc.io.dest_out
}

class xpm_cdc_single(
) extends ExtModule(Map(
  "DEST_SYNC_FF" -> 4,
  "INIT_SYNC_FF" -> 0,
  "SIM_ASSERT_CHK" -> 0,
  "SRC_INPUT_REG" -> 1,
)) {
  val io = FlatIO(new Bundle {
    val dest_clk = Input(Bool())
    val dest_out = Output(Bool())
    val src_clk = Input(Bool())
    val src_in = Input(Bool())
  })
}

