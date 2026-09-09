package net.gamebub.framework.interface

import chisel3._

class SramV0(
    addressWidth: Int = 18,
    dataWidth: Int = 16,
) extends Bundle {
  /** Chip Select (active-low) */
  val ceN = Output(Bool())
  /** Write enable (active-low) */
  val weN = Output(Bool())
  /** Output enable (active-low) */
  val oeN = Output(Bool())
  /** Byte write mask (active-low) */
  val writeMaskN = Output(UInt((dataWidth / 8).W))
  /** Address */
  val address = Output(UInt(addressWidth.W))
  /** Data input */
  val dataIn = Input(UInt(dataWidth.W))
  /** Data output */
  val dataOut = Output(UInt(dataWidth.W))
  /** Data direction: high for output, low for input */
  val dataDir = Output(Bool())
}
