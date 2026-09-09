package xilinx

import chisel3._

object XpmCdcHandshake {
  /**
   * Continuously synchronize a multi-bit signal from a
   * source clock domain to the current clock domain.
   */
  def continuous[T <: Data](sourceClock: Clock, sourceInput: T): T = {
    val cdc = Module(new XpmCdcHandshake[T](chiselTypeOf(sourceInput)))
    cdc.io.sourceClock := sourceClock
    cdc.io.sourceInput := sourceInput
    cdc.io.sourceSend := !cdc.io.sourceReceived
    cdc.io.destAck := DontCare
    cdc.io.destOutput
  }
}

class XpmCdcHandshake[T <: Data](gen: T, externalHandshake: Boolean = false) extends Module {
  val io = IO(new Bundle {
    /** Source clock */
    val sourceClock = Input(Clock())
    /** Source input */
    val sourceInput = Input(gen)
    /** Signal to source that the data has been received. */
    val sourceReceived = Output(Bool())
    /** Source requesting to send new data */
    val sourceSend = Input(Bool())

    /** Destination output */
    val destOutput = Output(gen)
    /** Destination signaling new data accepted (unused if !externalHandshake) */
    val destAck = Input(Bool())
    /** Signal to destination that new data has been received. */
    val destRequest = Output(Bool())
  })

  val cdc = Module(new xpm_cdc_handshake(gen.getWidth, externalHandshake))
  cdc.io.src_clk := io.sourceClock
  cdc.io.src_in := io.sourceInput.asUInt
  io.sourceReceived := cdc.io.src_rcv
  cdc.io.src_send := io.sourceSend.asBool

  cdc.io.dest_clk := clock
  io.destOutput := cdc.io.dest_out.asTypeOf(gen)
  cdc.io.dest_ack := io.destAck
  io.destRequest := cdc.io.dest_req
}

class xpm_cdc_handshake(
  width: Int,
  destExtHsk: Boolean,
  destSyncFf: Int = 2,
  srcSyncFf: Int = 2,
  initSyncFf: Boolean = true,
  simAssertChk: Boolean = true,
) extends ExtModule(Map(
  "DEST_EXT_HSK" -> (if (destExtHsk) 1 else 0),
  "DEST_SYNC_FF" -> destSyncFf,
  "INIT_SYNC_FF" -> (if (initSyncFf) 1 else 0),
  "SIM_ASSERT_CHK" -> (if (simAssertChk) 1 else 0),
  "SRC_SYNC_FF" -> srcSyncFf,
  "WIDTH" -> width,
)) {
  val io = FlatIO(new Bundle {
    val dest_ack = Input(Bool())
    val dest_clk = Input(Clock())
    val dest_out = Output(UInt(width.W))
    val dest_req = Output(Bool())
    val src_clk = Input(Clock())
    val src_in = Input(UInt(width.W))
    val src_rcv = Output(Bool())
    val src_send = Input(Bool())
  })
}