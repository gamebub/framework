package platform.handheld.spi

import chisel3._
import chisel3.util._

/** Raw SPI signals, unsynchronized. */
class SpiSignals extends Bundle {
  val serialClock = Input(Bool())
  val serialIn = Input(UInt(4.W))
  val serialOut = Output(UInt(4.W))
  val serialDir = Output(UInt(4.W))
  /** Active-low chip select */
  val chipSelect = Input(Bool())
}

object SpiLineWidth extends ChiselEnum {
  val single = Value
  val dual = Value
  val quad = Value
  // last value: single 3-wire?
}

class SpiCommand extends Bundle {
  /** Bit 5-6: Address and data line width */
  val lineWidth = SpiLineWidth()
  /** Bit 4: Autoincrement address by word size */
  val autoIncrement = Bool()
  /** Bit 3: 1 to enable byte swapping (within the word) */
  val byteSwap = Bool()
  /** Bit 1-2: 0=8, 1=16, 2=32, 3=64 (unimplemented) */
  val wordSize = UInt(2.W)
  /** Bit 0: 1 if the command is controller reading a register */
  val read = Bool()
}

/**
 * SPI receiver, half-duplex
 *
 * Chip-select: active-low
 * Polarity: 0 (clock idles low), data sampled on rising edge
 */
class SpiReceiver(
  commandLength: Int = 8,
  addressLength: Int = 16,
  maxDataLength: Int = 32,
) extends Module {
  val io = IO(new Bundle {
    val signals = new SpiSignals

    val address = Output(UInt(addressLength.W))
    val dataWrite = Output(UInt(maxDataLength.W))
    val dataRead = Input(UInt(maxDataLength.W))
    val readValid = Output(Bool())
    val writeValid = Output(Bool())
  })

  object State extends ChiselEnum {
    val writeCommand = Value
    val writeAddress = Value
    val writeData = Value
    val readData = Value
  }

  // Synchronized signals
  val serialClock = RegNext(RegNext(io.signals.serialClock))
  val serialIn = RegNext(RegNext(io.signals.serialIn))
  val chipSelect = RegNext(RegNext(io.signals.chipSelect))

  // State
  val shiftRegisterLength = commandLength.max(addressLength).max(maxDataLength)
  val state = Reg(State())
  val shiftInReg = Reg(UInt(shiftRegisterLength.W))
  val shiftInCounter = Reg(UInt((log2Ceil(shiftRegisterLength) + 1).W))
  val shiftOutReg = Reg(UInt(shiftRegisterLength.W))
  val shiftOutCounter = Reg(UInt((log2Ceil(shiftRegisterLength) + 1).W))
  val regCommand = Reg(new SpiCommand)
  val regAddress = Reg(UInt(addressLength.W))

  val wordSizeInBits = (8.U << regCommand.wordSize).asUInt

  // I/O
  io.signals.serialOut := VecInit(Seq(
    shiftOutReg(7), shiftOutReg(15), shiftOutReg(31), shiftOutReg(31),
  ))(regCommand.wordSize)
  io.address := regAddress
  io.dataWrite := Mux(regCommand.byteSwap,
    VecInit(Seq(
      shiftInReg(7, 0),
      Cat(shiftInReg(7, 0), shiftInReg(15, 8)),
      Cat(shiftInReg(7, 0), shiftInReg(15, 8), shiftInReg(23, 16), shiftInReg(31, 24)),
      shiftInReg, // XXX: 64-bit not implemented
    ))(regCommand.wordSize),
    shiftInReg
  )
  io.readValid := false.B
  io.writeValid := false.B

  val prevSerialClock = RegNext(serialClock)
  val risingClock = serialClock && !prevSerialClock
  val fallingClock = !serialClock && prevSerialClock

  when (!chipSelect) {
    // Chip activation: nCS falling edge
    when (RegNext(chipSelect)) {
      state := State.writeCommand
      shiftInCounter := commandLength.U
    }

    // Rising clock: sample data
    when (risingClock) {
      shiftInReg := Cat(shiftInReg, serialIn)
      shiftInCounter := shiftInCounter - 1.U
    }
    // Falling clock: shift out data
    when (fallingClock) {
      when (state === State.readData && shiftOutCounter === 0.U) {
        // Read the next data.
        io.readValid := true.B
        shiftOutReg := Mux(regCommand.byteSwap,
          VecInit(Seq(
            io.dataRead(7, 0),
            Cat(io.dataRead(7, 0), io.dataRead(15, 8)),
            Cat(io.dataRead(7, 0), io.dataRead(15, 8), io.dataRead(23, 16), io.dataRead(31, 24)),
            io.dataRead, // XXX: 64-bit not implemented
          ))(regCommand.wordSize),
          io.dataRead
        )

        shiftOutCounter := wordSizeInBits - 1.U
        when (regCommand.autoIncrement) {
          regAddress := regAddress + (1.U << regCommand.wordSize).asUInt
        }
      }.otherwise {
        shiftOutReg := shiftOutReg << 1
        shiftOutCounter := shiftOutCounter - 1.U
      }
    }

    when(shiftInCounter === 0.U) {
      switch(state) {
        is (State.writeCommand) {
          // Finished writing command
          regCommand := shiftInReg.asTypeOf(new SpiCommand)
          state := State.writeAddress
          shiftInCounter := addressLength.U
        }
        is (State.writeAddress) {
          // Finished writing address
          regAddress := shiftInReg
          shiftInReg := 0.U
          shiftInCounter := wordSizeInBits
          shiftOutCounter := 0.U

          when(regCommand.read) {
            state := State.readData
          }.otherwise {
            state := State.writeData
          }
        }
        is (State.writeData) {
          // Finished writing data
          io.writeValid := true.B
          shiftInReg := 0.U
          shiftInCounter := wordSizeInBits
          when (regCommand.autoIncrement) {
            regAddress := regAddress + (1.U << regCommand.wordSize).asUInt
          }
        }
      }
    }
  }
}
