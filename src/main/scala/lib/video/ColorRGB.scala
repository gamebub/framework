package lib.video

import chisel3._
import chisel3.util._

object ColorRGB {
  def apply(r: Int, g: Int, b: Int): ColorRGB = {
    new ColorRGB(r, g, b)
  }

  def apply(depth: Int): ColorRGB = ColorRGB(depth, depth, depth)
}

class ColorRGB(rWidth: Int, gWidth: Int, bWidth: Int) extends Color {
  val r = UInt(rWidth.W)
  val g = UInt(gWidth.W)
  val b = UInt(bWidth.W)

  def make(r: Int, g: Int, b: Int): ColorRGB = {
    val c = Wire(this)
    c.r := r.U
    c.g := g.U
    c.b := b.U
    c
  }

  override def convertTo[T](gen: T): T = gen match {
    case c: ColorARGB => {
      val out = Wire(c.cloneType)
      out.a := Color.convertA(0.U(0.W), c.a)
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
