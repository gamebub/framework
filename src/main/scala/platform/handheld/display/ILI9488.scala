package platform.handheld.display

import chisel3._
import chisel3.util._

object ILI9488 {
    def getClockDisplayHz(framePeriod: Double): (Int, Int) = {
        // TODO: this is for 60 Hz, calculate dynamically
        (12_000_000, 17_000_000)
    }
}

/** Display Driver for the ILI9488 */
class ILI9488(
  /** Display clock (Hz) */
  clockHz: Int,
  /** Typical source frame period (seconds)  */
  sourceFramePeriod: Double,
) extends Module {
  val hActive = 320
  val vActive = 480

  val io = IO(new DisplayDriverIO(hActive, vActive))

  val currentFrame = RegInit(0.U(1.W))
  /// Whether the display is currently synchronized with the render
  val regLocked = RegInit(true.B)
  val newFrameReady = io.lastRenderedFrame =/= currentFrame
  io.displayFrame := currentFrame

  val regHsync = RegInit(true.B)
  val regVsync = RegInit(true.B)
  val regActive = RegInit(false.B)
  io.signals.dotclk := clock
  io.signals.hsync := regHsync
  io.signals.vsync := regVsync
  io.signals.enable := regActive

  /*
  ILI9488:
  hsync + hbp < 192
  hfp <= 255
  vsync + vbp + vfp < 32

  "Recommendation: The porch number of VBP + VFP must be even."
  */

  // Calculate timing
  val vSync = 1
  val vBackPorch = 2
  val vFrontPorchMin = 2
  val vFrontPorchMax = 32 - vSync - vBackPorch - 1
  val hSync = 3
  val hBackPorch = 3

  val totalHeightMin = vActive + vSync + vBackPorch + vFrontPorchMin
  val totalHeightMax = vActive + vSync + vBackPorch + vFrontPorchMax

  // With minimum height, target 99% of the sourceFramePeriod
  val minFrameCycles = 0.99 * clockHz * sourceFramePeriod
  val totalWidth = (minFrameCycles / totalHeightMin).round.toInt
  val hFrontPorch = totalWidth - (hActive + hSync + hBackPorch)

  assert(hFrontPorch >= 3)
  assert(hFrontPorch <= 255)
  assert(hBackPorch >= 3)
  assert(hSync + hBackPorch < 192)
  assert(totalWidth * totalHeightMin / sourceFramePeriod < clockHz)
  assert(totalWidth * totalHeightMax / sourceFramePeriod > clockHz)

  val x = RegInit(0.U(log2Ceil(totalWidth).W))
  val y = RegInit(0.U(log2Ceil(totalHeightMax).W))
  io.pixelX := x - (hSync + hBackPorch).U
  io.pixelY := y - (vSync + vBackPorch).U

  when (x === (totalWidth - 1).U) {
    // Scanline is done
    regHsync := true.B
    x := 0.U
    y := y + 1.U

    val startFrame = WireDefault(false.B)
    when (startFrame) {
      regVsync := true.B
      y := 0.U
      currentFrame := io.lastRenderedFrame
    }

    when (!regLocked && newFrameReady) {
      // Immediately display new frame (interrupting current frame)
      regLocked := true.B
      startFrame := true.B
    } .elsewhen (y === (vSync - 1).U) {
      regVsync := false.B
    } .elsewhen ((y >= (totalHeightMin - 1).U) && newFrameReady) {
      // New frame available, start rendering.
      startFrame := true.B
    } .elsewhen (y === (totalHeightMax - 1).U) {
      // Hit the maximum allowed total height without a new frame coming in:
      // source is too slow, switch to rapid refresh (no longer locked)
      regLocked := false.B
      startFrame := true.B
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
