package net.gamebub.framework.interface

import chisel3._

object VibrateV0 {
  object Mode extends ChiselEnum {
    val Off, On, Brake = Value
  }
}

class VibrateV0(
) extends Bundle {
  val mode = Output(VibrateV0.Mode())
}
