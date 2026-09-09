package net.gamebub.framework.interface

import chisel3._
import chisel3.util._
import lib.video.ColorRGB

class VideoFilterBasicV0(
  /// Color depth of the input video (red channel), must match Video interface
  val colorInDepthR: Int,
  /// Color depth of the input video (green channel), must match Video interface
  val colorInDepthG: Int,
  /// Color depth of the input video (blue channel), must match Video interface
  val colorInDepthB: Int,
  /// Latency (in cycles) from filter input to output (pipeline depth)
  val latency: Int,
) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Reset())

  val dataIn = Input(ColorRGB(colorInDepthR, colorInDepthG, colorInDepthB))

  val dataOut = Output(ColorRGB(8))
}
