package lib.video

import chisel3._
import chisel3.util._

trait Color extends Bundle {
  def convertTo[T](gen: T): T
}

object Color {
  def convertRGB(from: UInt, to: UInt): UInt = {
    if (from.getWidth < to.getWidth) {
      val repeat = (to.getWidth.toFloat / from.getWidth.toFloat).ceil.toInt
      Fill(repeat, from).head(to.getWidth)
    } else {
      from.head(to.getWidth)
    }
  }

  def convertA(from: UInt, to: UInt): UInt = {
    if (to.getWidth == 0) {
      0.U(0.W)
    } else if (from.getWidth == 0) {
      (1 << to.getWidth - 1).U(to.getWidth.W)
    } else {
      convertRGB(from, to)
    }
  }
}