package xilinx

import chisel3._

object XpmCdcSyncRst {
  /**
   * Synchronize a reset signal to the destination clock domain.
   */
  def apply(sourceReset: Reset): Reset = {
    val cdc = Module(new XpmCdcSyncRst)
    cdc.io.sourceReset := sourceReset
    cdc.io.destReset
  }
}

class XpmCdcSyncRst extends Module {
  val io = IO(new Bundle {
    /** Source reset */
    val sourceReset = Input(Reset())

    /** Destination reset */
    val destReset = Output(Bool())
  })

  val cdc = Module(new xpm_cdc_sync_rst())
  cdc.io.src_rst := io.sourceReset
  cdc.io.dest_clk := clock.asBool
  io.destReset := cdc.io.dest_rst
}

class xpm_cdc_sync_rst(
) extends ExtModule(Map(
  "DEST_SYNC_FF" -> 4,
  "INIT" -> 1,
  "INIT_SYNC_FF" -> 0,
  "SIM_ASSERT_CHK" -> 0,
)) {
  val io = FlatIO(new Bundle {
    val dest_clk = Input(Bool())
    val dest_rst = Output(Bool())
    val src_rst = Input(Reset())
  })
}

