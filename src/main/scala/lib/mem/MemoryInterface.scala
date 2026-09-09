package lib.mem

import chisel3._

class MemoryInterface(addressWidth: Int, dataWidth: Int) extends Bundle {
  /** Access address */
  val address = Input(UInt(addressWidth.W))
  /** Access enable */
  val enable = Input(Bool())
  /** Whether the access is a write */
  val write = Input(Bool())
  /** Read data */
  val dataRead = Output(UInt(dataWidth.W))
  /** Write data */
  val dataWrite = Input(UInt(dataWidth.W))
  /** Write data byte strobe. Ignored by some targets. */
  val writeStrobe = Input(UInt((dataWidth / 8).W))
  /** True when the access is complete. */
  val done = Output(Bool())
}
