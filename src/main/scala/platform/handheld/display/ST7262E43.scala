package platform.handheld.display

import chisel3._
import chisel3.util._

object ST7262 {
    def getClockDisplayHz(framePeriod: Double): (Int, Int) = {
        (26_099_000, 26_100_000)
    }
}

/** Display Driver for the ST7262E43 */
class ST7262E43(
  /** Display clock (Hz) */
  clockHz: Int,
  /** Typical source frame period (seconds)  */
  sourceFramePeriod: Double,
) extends Module {
  val hActive = 800
  val vActive = 400
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

  // Calculate timing
  val vSyncMin = 2
  val vSync = vSyncMin + 2
  val vBackPorch = 4
  val vFrontPorchMin = 4
  val vFrontPorchMax = 12
  val hSync = 2
  val hBackPorch = 4

  // Target ~99.5% the sourceFramePeriod, and make up the rest with frozen cycles.
  val totalHeightMin = vActive + vSync + vBackPorch + vFrontPorchMin
  val totalHeightMax = vActive + vSync + vBackPorch + vFrontPorchMax
  val minFrameCycles = 0.995 * clockHz * sourceFramePeriod
  val approxFrameWidth = (minFrameCycles / totalHeightMin).round.toInt
  val hFrontPorch = approxFrameWidth - (hActive + hSync + hBackPorch)
  val totalWidth = hActive + hSync + hBackPorch + hFrontPorch
  val totalHeight = vActive + vSync + vBackPorch + vFrontPorchMin
  // Maximum number of cycles the clock can be stopped before artifacts occur.
  val maximumFrozenCycles = totalWidth * 6

  /// Timer for stopping the dot clock.
  val freezeTimer = RegInit(0.U(16.W))
  io.signals.dotclk := (clock.asBool & RegNext(freezeTimer === 0.U)).asClock

  val x = RegInit(0.U(log2Ceil(totalWidth).W))
  val y = RegInit(0.U(log2Ceil(totalHeightMax).W))
  io.pixelX := x - (hSync + hBackPorch).U
  io.pixelY := y - (vSync + vBackPorch).U

  when (freezeTimer > 0.U) {
    freezeTimer := freezeTimer - 1.U

    when (newFrameReady) {
      currentFrame := io.lastRenderedFrame
      freezeTimer := 0.U
    } .elsewhen (freezeTimer === 1.U) {
      // TODO if freezeTimer expires without a new frame being ready,
      // consider switching refresh rate or similar to re-synchronize.
    }
  } .elsewhen (x === (totalWidth - 1).U) {
    // Scanline is done
    regHsync := false.B
    x := 0.U
    y := y + 1.U

    when (y === (vSync - 1).U) {
      regVsync := true.B
    } .elsewhen (y === (totalHeight - 1).U) {
      // Hit the regular number of display cycles.
      // Freeze the clock until the next frame comes in.
      freezeTimer := maximumFrozenCycles.U
      regVsync := false.B
      y := 0.U
    }
  } .otherwise {
    x := x + 1.U
    when (x === (hSync - 1).U) {
      regHsync := true.B
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
