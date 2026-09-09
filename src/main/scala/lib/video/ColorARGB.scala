package lib.video

import chisel3._
import chisel3.util._

object ColorARGB {
  def apply(a: Int, r: Int, g: Int, b: Int): ColorARGB = {
    new ColorARGB(a, r, g, b)
  }

  def rgb555(): ColorARGB = ColorARGB(0, 5, 5, 5)

  def argb1555(): ColorARGB = ColorARGB(1, 5, 5, 5)
}

class ColorARGB(aWidth: Int, rWidth: Int, gWidth: Int, bWidth: Int) extends Color {
  val a = UInt(aWidth.W)
  val r = UInt(rWidth.W)
  val g = UInt(gWidth.W)
  val b = UInt(bWidth.W)

  def makeBlack(): ColorARGB = {
    val c = Wire(this)
    c.a := ((1 << aWidth) - 1).U
    c.r := 0.U
    c.g := 0.U
    c.b := 0.U
    c
  }

  override def convertTo[T](gen: T): T = gen match {
    case c: ColorARGB => {
      val out = Wire(c.cloneType)
      out.a := Color.convertA(a, c.a)
      out.r := Color.convertRGB(r, c.r)
      out.g := Color.convertRGB(g, c.g)
      out.b := Color.convertRGB(b, c.b)
      out.asInstanceOf[T]
    }
    case c: ColorRGB => {
      val out = Wire(c.cloneType)
      out.r := Color.convertRGB(r, c.r)
      out.g := Color.convertRGB(g, c.g)
      out.b := Color.convertRGB(b, c.b)
      out.asInstanceOf[T]
    }
  }
}
