package net.gamebub.framework.interface

import chisel3._

class AudioV0(
) extends Bundle {
  val left = Output(SInt(16.W))
  val right = Output(SInt(16.W))
}
