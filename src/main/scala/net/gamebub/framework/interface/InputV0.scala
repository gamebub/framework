package net.gamebub.framework.interface

import chisel3._

object InputV0 {
  class Buttons extends Bundle {
    val a = Bool()
    val b = Bool()
    val x = Bool()
    val y = Bool()
    val up = Bool()
    val down = Bool()
    val left = Bool()
    val right = Bool()
    val l = Bool()
    val r = Bool()
    val start = Bool()
    val select = Bool()
  }
}

class InputV0(
) extends Bundle {
  val buttons = Input(new InputV0.Buttons())
}
