package xilinx

import chisel3._
import chisel3.util._

class XpmFifoAsync[T <: Data](
  gen: => T,
  /** The number of entries in the FIFO. */
  val depth: Int,
) extends RawModule {
  val io = IO(new Bundle {
    /** Write clock */
    val writeClock = Input(Clock())
    /** Write data input */
    val dataIn = Input(gen)
    /** Whether the FIFO is full (if true, cannot write) */
    val full = Output(Bool())
    /** Whether to write the next entry. */
    val writeEnable = Input(Bool())
    /** Whether the write side is busy due to a reset. */
    val writeResetBusy = Output(Bool())
    /** Active-high reset of the FIFO, write side. */
    val reset = Input(Bool())

    /** Read clock */
    val readClock = Input(Clock())
    /** Read data output */
    val dataOut = Output(gen)
    /** Whether the FIFO is empty (if true, cannot read) */
    val empty = Output(Bool())
    /** Whether to read the next entry. */
    val readEnable = Input(Bool())
    /** Whether the read side is busy due to a reset. */
    val readResetBusy = Output(Bool())
  })

  val fifo = Module(new xpm_fifo_async(
    readDataWidth = gen.getWidth,
    writeDataWidth = gen.getWidth,
    writeDepth = depth,
    memoryType = "block",
    readMode = "fwft",
  ))

  fifo.io.injectdbiterr := false.B
  fifo.io.injectsbiterr := false.B
  fifo.io.rst := io.reset
  fifo.io.sleep := false.B

  fifo.io.rd_clk := io.readClock.asBool
  fifo.io.rd_en := io.readEnable
  fifo.io.wr_clk := io.writeClock.asBool
  fifo.io.wr_en := io.writeEnable
  fifo.io.din := io.dataIn.asUInt
  io.dataOut := fifo.io.dout.asTypeOf(gen)
  io.full := fifo.io.full
  io.empty := fifo.io.empty
  io.readResetBusy := fifo.io.rd_rst_busy
  io.writeResetBusy := fifo.io.wr_rst_busy
}

class xpm_fifo_async(
  readDataWidth: Int = 32,
  writeDataWidth: Int = 32,
  writeDepth: Int = 2048,
  advFeatures: String = "0707",
  memoryType: String = "auto",
  readMode: String = "fwft" /* or "std" */,
) extends ExtModule(Map(
  "CASCADE_HEIGHT" -> 0,
  "CDC_SYNC_STAGES" -> 2,
  "DOUT_RESET_VALUE" -> "0",
  "ECC_MODE" -> "no_ecc",
  "FIFO_MEMORY_TYPE" -> memoryType,
  "FIFO_READ_LATENCY" -> 1,
  "FIFO_WRITE_DEPTH" -> writeDepth,
  "FULL_RESET_VALUE" -> 0,
  "PROG_EMPTY_THRESH" -> 10,
  "PROG_FULL_THRESH" -> 10,
  "RD_DATA_COUNT_WIDTH" -> (log2Ceil((writeDepth * writeDataWidth) / readDataWidth) + 1),
  "READ_DATA_WIDTH" -> readDataWidth,
  "READ_MODE" -> readMode,
  "RELATED_CLOCKS" -> 0,
  "SIM_ASSERT_CHK" -> 0,
  "USE_ADV_FEATURES" -> advFeatures,
  "WAKEUP_TIME" -> 0,
  "WRITE_DATA_WIDTH" -> writeDataWidth,
  "WR_DATA_COUNT_WIDTH" -> (log2Ceil(writeDepth) + 1),
)) {
  val io = FlatIO(new Bundle {
    val almost_empty = Output(Bool())
    val almost_full = Output(Bool())
    val data_valid = Output(Bool())
    val dbiterr = Output(Bool())
    val dout = Output(UInt(readDataWidth.W))
    val empty = Output(Bool())
    val full = Output(Bool())
    val overflow = Output(Bool())
    val prog_empty = Output(Bool())
    val prog_full = Output(Bool())
    val rd_data_count = Output(UInt((log2Ceil(writeDepth) + 1).W))
    val rd_rst_busy = Output(Bool())
    val sbiterr = Output(Bool())
    val underflow = Output(Bool())
    val wr_ack = Output(Bool())
    val wr_data_count = Output(UInt((log2Ceil((writeDepth * writeDataWidth) / readDataWidth) + 1).W))
    val wr_rst_busy = Output(Bool())
    val din = Input(UInt(writeDataWidth.W))
    val injectdbiterr = Input(Bool())
    val injectsbiterr = Input(Bool())
    val rd_clk = Input(Bool())
    val rd_en = Input(Bool())
    val rst = Input(Bool())
    val sleep = Input(Bool())
    val wr_clk = Input(Bool())
    val wr_en = Input(Bool())
  })
}

