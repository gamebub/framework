package platform.handheld.display

import chisel3._
import chisel3.util._

class DisplayDriverIO(hActive: Int, vActive: Int) extends Bundle {
    val signals = Output(new DpiSignals)

    val pixelX = Output(UInt(log2Ceil(hActive).W))
    val pixelY = Output(UInt(log2Ceil(vActive).W))

    /// Last rendered frame index
    val lastRenderedFrame = Input(UInt(1.W))
    /// Current display frame index
    val displayFrame = Output(UInt(1.W))
}