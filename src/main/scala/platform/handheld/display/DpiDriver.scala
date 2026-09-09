package platform.handheld.display

import chisel3._
import chisel3.util._

class DpiSignals extends Bundle {
  /** Active high */
  val vsync = Bool()
  /** Active high */
  val hsync = Bool()
  /** Active high */
  val enable = Bool()
  val dotclk = Clock()
}

/**
 * Display Parallel Interface (DPI) driver.
 * Manages the control signals, caller sends the pixel data.
 */
class DpiDriver(
  /** Width of the active area (pixels) */
  hActive: Int,
  /** Length of the horizontal sync period (clocks) */
  hSync: Int,
  /** Length of the horizontal back porch (clocks) */
  hBackPorch: Int,
  /** Length of the horizontal front porch (clocks) */
  hFrontPorch: Int,
  /** Height of the active area (pixels) */
  vActive: Int,
  /** Length of the vertical sync period (lines) */
  vSync: Int,
  /** Length of the vertical back porch (lines) */
  vBackPorch: Int,
  /** Length of the vertical front porch (lines) */
  vFrontPorch: Int,
) extends Module {
  val io = IO(new Bundle {
    val signals = Output(new DpiSignals)

    val pixelX = Output(UInt(log2Ceil(hActive).W))
    val pixelY = Output(UInt(log2Ceil(vActive).W))

    /// Pulsed high at the beginning of the first scanline (before pixels are drawn).
    val frameStart = Output(Bool())
  })

  assert(hActive > 0)
  assert(hSync > 0)
  assert(hBackPorch > 0)
  assert(hFrontPorch > 0)
  assert(vActive > 0)
  assert(vSync > 0)
  assert(vBackPorch > 0)
  assert(vFrontPorch > 0)
  val totalWidth = hActive + hSync + hBackPorch + hFrontPorch
  val totalHeight = vActive + vSync + vBackPorch + vFrontPorch

  val regHsync = RegInit(true.B)
  val regVsync = RegInit(true.B)
  val regActive = RegInit(false.B)
  io.signals.dotclk := clock
  io.signals.hsync := regHsync
  io.signals.vsync := regVsync
  io.signals.enable := regActive

  val x = RegInit(0.U(log2Ceil(totalWidth).W))
  val y = RegInit(0.U(log2Ceil(totalHeight).W))
  io.pixelX := x - (hSync + hBackPorch).U
  io.pixelY := y - (vSync + vBackPorch).U
  io.frameStart := false.B

  when (x === (totalWidth - 1).U) {
    regHsync := true.B
    x := 0.U
    when (y === (totalHeight - 1).U) {
      regVsync := true.B
      y := 0.U
    } .otherwise {
      y := y + 1.U
      when (y === (vSync - 1).U) {
        regVsync := false.B
      }
      when (y === (vSync + vBackPorch - 1).U) {
        io.frameStart := true.B
      }
    }
  } .otherwise {
    x := x + 1.U
    when (x === (hSync - 1).U) {
      regHsync := false.B
    }
    val isVActive = (y >= (vSync + vBackPorch).U) && (y < (vSync + vBackPorch + vActive).U)
    when (x === (hSync + hBackPorch - 1).U && isVActive) {
      regActive := true.B
    }
    when (x === (hSync + hBackPorch + hActive - 1).U) {
      regActive := false.B
    }
  }
}
