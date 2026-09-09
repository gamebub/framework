package net.gamebub.framework.interface

import chisel3._

class PmodV0(
) extends Bundle {
  val in = Input(UInt(4.W))
  val out = Output(UInt(4.W))
  val dir = Output(UInt(4.W))
}
