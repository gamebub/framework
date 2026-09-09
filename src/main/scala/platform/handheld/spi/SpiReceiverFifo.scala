package platform.handheld.spi

import chisel3._
import chisel3.util._
import lib.mem.MemoryInterface
import lib.util.ResetSynchronizer
import xilinx.{XpmCdcSingle, XpmFifoAsync}
import platform.handheld.spi.{SpiCommand, SpiLineWidth, SpiSignals}

/**
 * SPI receiver with a FIFO for CDC.
 *
 * This allows the system clock speed to be slower than the SPI clock speed.
 * A "SPI receiver clock" is passed in, which is used to sample the SPI signals, and then
 * a FIFO is used to synchronize memory accesses to the system clock.
 *
 * The SPI receiver clock can be powered down, and only powered up when the chip-select
 * signal is asserted.
 *
 * Note: the maximum supported SPI clock (as in, the CLK SPI signal) is dependent on the
 * receiver clock. For *write* transactions (from the controller to the receiver), the
 * SPI clock must be at most 1/4 the receiver clock. That is, for a 200 MHz receiver clock,
 * the maximum rate is 50 MHz (or ideally a bit lower).
 * The maximum *read* speed is lower: the SPI clock is synchronized with a double-flip flop,
 * and passed to an edge detector. This adds 3 clock latency + an additional clock for updating
 * the SPI output buffer *after* a falling edge. Since the controller samples at the rising
 * edge, this means that the clock must be low for at least 4 receiver clock cycles. Thus,
 * the SPI clock during a read should be at most 1/10 the receiver clock.
 */
class SpiReceiverFifo(
  commandWidth: Int = 8,
  addressWidth: Int = 32,
  dataWidth: Int = 32,
  dummyBytes: Int = 8,
) extends Module {
  val io = IO(new Bundle {
    val signals = new SpiSignals

    /** Interface for SPI receiver to access device memory. */
    val mem = Flipped(new MemoryInterface(addressWidth, dataWidth))

    /** Clock for the SPI receiver domain */
    val clockSpi = Input(Clock())
    /** Whether the SPI clock is locked. */
    val clockSpiLocked = Input(Bool())
    /** Whether the SPI clock should be powered down. */
    val clockSpiPowerDown = Output(Bool())

    /** Whether a request FIFO overflow happened in the current transaction. */
    val debugRequestOverflow = Output(Bool())
    /** Whether a response FIFO underflow happened in the current transaction. */
    val debugResponseUnderflow = Output(Bool())
  })

  class FifoRequest extends Bundle {
    val isStart = Bool()
    val inner = UInt(35.W)
  }

  class FifoRequestStart extends Bundle {
    val write = Bool()
    val wordSize = UInt(2.W)
    val address = UInt(dataWidth.W)
  }

  class FifoRequestContinue extends Bundle {
    val autoincrement = Bool() // XXX: can this just be the 0b11 value in wordSize?
    val unused = UInt(2.W)
    val data = UInt(dataWidth.W)
  }

  object State extends ChiselEnum {
    val init = Value
    val writeCommand = Value
    val writeAddress = Value
    val writeData = Value
    val readData = Value
  }

  val spiClock = io.clockSpi
  val systemClock = clock

  // FIFOs
  // Request is SPI -> System, Response is System -> SPI
  val fifoRequest = Module(new XpmFifoAsync(new FifoRequest, 512))
  fifoRequest.io.writeClock := spiClock
  fifoRequest.io.readClock := systemClock
  fifoRequest.io.writeEnable := false.B
  fifoRequest.io.readEnable := false.B
  fifoRequest.io.dataIn := DontCare
  fifoRequest.io.reset := false.B
  val fifoResponse = Module(new XpmFifoAsync(UInt(32.W), depth = 512))
  fifoResponse.io.writeClock := systemClock
  fifoResponse.io.readClock := spiClock
  fifoResponse.io.writeEnable := false.B
  fifoResponse.io.readEnable := false.B
  fifoResponse.io.dataIn := DontCare
  fifoResponse.io.reset := false.B
  val fifoResponseUnderflow = withClock(spiClock)(Reg(Bool()))
  val fifoRequestOverflow = withClock(spiClock)(Reg(Bool()))

  withClock(spiClock) {

    val spiReset = ResetSynchronizer((!io.clockSpiLocked).asAsyncReset)
    withReset(spiReset) {
      // Synchronized signals
      val serialClock = RegNext(RegNext(io.signals.serialClock))
      val serialIn = RegNext(RegNext(io.signals.serialIn))
      val chipSelect = RegNext(RegNext(io.signals.chipSelect))

      // SPI State
      val shiftRegisterLength = commandWidth.max(addressWidth).max(dataWidth)
      val state = RegInit(State.init)
      val regLineWidth = RegInit(SpiLineWidth.single)
      val shiftInReg = Reg(UInt(shiftRegisterLength.W))
      val shiftInCounter = Reg(UInt((log2Ceil(shiftRegisterLength) + 1).W))
      val shiftOutReg = Reg(UInt(shiftRegisterLength.W))
      val shiftOutCounter = Reg(UInt((log2Ceil(shiftRegisterLength) + 1).W))
      val regCommand = Reg(new SpiCommand)
      val regDummyTimer = Reg(UInt((log2Ceil(dummyBytes) + 1).W))

      val lineWidthInBits = WireDefault(1.U(3.W))
      val wordSizeInBits = (8.U << regCommand.wordSize).asUInt

      val dataOut = Wire(UInt(4.W))
      io.signals.serialOut := DontCare
      io.signals.serialDir := 0.U
      switch (regLineWidth) {
        is (SpiLineWidth.single) {
          // D0: data input, D1: data output
          lineWidthInBits := 1.U
          io.signals.serialOut := Cat(dataOut(3), 0.U(1.W))
          io.signals.serialDir := "b0010".U(4.W)
        }
        is (SpiLineWidth.dual) {
          lineWidthInBits := 2.U
          io.signals.serialOut := Cat(dataOut(3), dataOut(2))
          io.signals.serialDir := Mux(state === State.readData, "b0011".U(4.W), 0.U)
        }
        is (SpiLineWidth.quad) {
          lineWidthInBits := 4.U
          io.signals.serialOut := Cat(dataOut(3), dataOut(2), dataOut(1), dataOut(0))
          io.signals.serialDir := Mux(state === State.readData, "b1111".U(4.W), 0.U)
        }
      }

      // When true, keeps the fast clock powered up even if nCS goes high.
      val keepAlive = RegInit(false.B)
      io.clockSpiPowerDown := io.signals.chipSelect && !keepAlive

      // SPI I/O
      dataOut := VecInit(Seq(
        shiftOutReg(7, 4), shiftOutReg(15, 12), shiftOutReg(31, 28), shiftOutReg(31, 28),
      ))(regCommand.wordSize)

      val prevSerialClock = RegNext(serialClock)
      val risingClock = serialClock && !prevSerialClock
      val fallingClock = !serialClock && prevSerialClock
      when(!chipSelect) {
        // Chip activation: nCS falling edge
        when(RegNext(chipSelect, true.B)) {
          state := State.writeCommand
          shiftInCounter := commandWidth.U
          fifoRequestOverflow := false.B
          fifoResponseUnderflow := false.B
          keepAlive := true.B
        }

        // Rising clock: sample data
        when(risingClock) {
          switch (regLineWidth) {
            is (SpiLineWidth.single) {
              shiftInReg := Cat(shiftInReg, serialIn(0))
            }
            is (SpiLineWidth.dual) {
              shiftInReg := Cat(shiftInReg, serialIn(1, 0))
            }
            is (SpiLineWidth.quad) {
              shiftInReg := Cat(shiftInReg, serialIn(3, 0))
            }
          }
          shiftInCounter := shiftInCounter - lineWidthInBits
        }
        // Falling clock: shift out data
        when(fallingClock) {
          when(state === State.readData && shiftOutCounter === 0.U) {
            // Read the next data.

            // Push read request to FIFO.
            val request = Wire(new FifoRequestContinue)
            request.unused := DontCare
            request.autoincrement := regCommand.autoIncrement
            request.data := DontCare
            fifoRequest.io.dataIn.isStart := false.B
            fifoRequest.io.dataIn.inner := request.asUInt
            when (fifoRequest.io.full) {
              fifoRequestOverflow := true.B
            } .elsewhen(!fifoRequestOverflow) {
              // Push the request.
              // This happens only if there hasn't been an overflow in this transaction, otherwise there's
              // a good chance the request will be misinterpreted (due to data loss) and cause unexpected writes.
              fifoRequest.io.writeEnable := true.B
            }

            when(regDummyTimer === 0.U) {
              // Read a real word.
              when(fifoResponse.io.empty) {
                shiftOutReg := "hFFFFFFFF".U
                fifoResponseUnderflow := true.B
              }.otherwise {
                // There's a response word present.
                fifoResponse.io.readEnable := true.B
                val data = fifoResponse.io.dataOut

                shiftOutReg := Mux(regCommand.byteSwap,
                  VecInit(Seq(
                    data(7, 0),
                    Cat(data(7, 0), data(15, 8)),
                    Cat(data(7, 0), data(15, 8), data(23, 16), data(31, 24)),
                    data, // XXX: 64-bit not implemented
                  ))(regCommand.wordSize),
                  data
                )
              }
            }.otherwise {
              shiftOutReg := "hFFFFFFFF".U
              regDummyTimer := regDummyTimer - 1.U
            }

            shiftOutCounter := wordSizeInBits - lineWidthInBits
          }.otherwise {
            shiftOutReg := shiftOutReg << lineWidthInBits
            shiftOutCounter := shiftOutCounter - lineWidthInBits
          }
        }

        when(shiftInCounter === 0.U) {
          switch(state) {
            is(State.writeCommand) {
              // Finished writing command
              val command = suppressEnumCastWarning {
                shiftInReg.asTypeOf(new SpiCommand)
              }
              regCommand := command
              state := State.writeAddress
              shiftInCounter := addressWidth.U
              regLineWidth := command.lineWidth
            }
            is(State.writeAddress) {
              // Finished writing address.
              val address = shiftInReg
              shiftInCounter := wordSizeInBits
              shiftOutCounter := 0.U

              // Push start transfer to request FIFO.
              val request = Wire(new FifoRequestStart)
              request.write := !regCommand.read
              request.wordSize := regCommand.wordSize
              request.address := address
              fifoRequest.io.dataIn.isStart := true.B
              fifoRequest.io.dataIn.inner := request.asUInt
              when(fifoRequest.io.full) {
                fifoRequestOverflow := true.B
              } .otherwise {
                fifoRequest.io.writeEnable := true.B
              }

              when(regCommand.read) {
                state := State.readData
                regDummyTimer := (dummyBytes.U >> regCommand.wordSize)
              }.otherwise {
                state := State.writeData
              }
            }
            is(State.writeData) {
              // Finished writing data.
              val data = shiftInReg

              val request = Wire(new FifoRequestContinue)
              request.unused := DontCare
              request.autoincrement := regCommand.autoIncrement
              request.data := Mux(regCommand.byteSwap,
                VecInit(Seq(
                  data(7, 0),
                  Cat(data(7, 0), data(15, 8)),
                  Cat(data(7, 0), data(15, 8), data(23, 16), data(31, 24)),
                  data, // XXX: 64-bit not implemented
                ))(regCommand.wordSize),
                data
              )
              fifoRequest.io.dataIn.isStart := false.B
              fifoRequest.io.dataIn.inner := request.asUInt
              when(fifoRequest.io.full) {
                fifoRequestOverflow := true.B
              } .elsewhen (!fifoRequestOverflow) {
                fifoRequest.io.writeEnable := true.B
              }

              shiftInCounter := wordSizeInBits
            }
          }
        }
      }.otherwise {
        state := State.init

        // Don't turn off the SPI clock PLL until the request FIFO has been drained.
        val fifoRequestEmpty = XpmCdcSingle(clock, fifoRequest.io.empty)
        when (fifoRequestEmpty) {
          keepAlive := false.B
        }
      }
    }
  }

  /**
   * System interface logic: runs in system clock domain,
   * operates on the request and response FIFOs.
   */
  val regSysAddress = Reg(UInt(dataWidth.W))
  val regSysWordSize = Reg(UInt(2.W))
  val regSysWrite = Reg(Bool())
  /** Chip select synchronized into system clock domain. */
  val sysChipSelect = RegNext(RegNext(io.signals.chipSelect))
  val regSysMemBusy = RegInit(false.B)

  io.mem.address := regSysAddress
  io.mem.enable := false.B
  io.mem.write := false.B
  io.mem.dataWrite := DontCare
  io.mem.writeStrobe := DontCare

  when (!fifoRequest.io.empty && !fifoRequest.io.readResetBusy) {
    when (fifoRequest.io.dataOut.isStart) {
      val request = fifoRequest.io.dataOut.inner.asTypeOf(new FifoRequestStart)
      regSysAddress := request.address
      regSysWordSize := request.wordSize
      regSysWrite := request.write
      fifoRequest.io.readEnable := true.B
    } .otherwise {
      val request = fifoRequest.io.dataOut.inner.asTypeOf(new FifoRequestContinue)
      io.mem.enable := true.B
      io.mem.write := regSysWrite
      io.mem.dataWrite := request.data
      // TODO: determine based on addresses
      io.mem.writeStrobe := "b1111".U

      when (regSysMemBusy) {
        when (io.mem.done) {
          regSysMemBusy := false.B

          // A write or read operation completed, so confirm and increment.
          fifoRequest.io.readEnable := true.B
          when (request.autoincrement) {
            regSysAddress := regSysAddress + (1.U << regSysWordSize).asUInt
          }

          // Pass read data through the FIFO.
          when (!regSysWrite) {
            fifoResponse.io.writeEnable := true.B
            fifoResponse.io.dataIn := io.mem.dataRead
          }
        }
      } .otherwise {
        regSysMemBusy := true.B
      }
    }
  }

  val regSysPendingReset = RegInit(false.B)
  when (!sysChipSelect && RegNext(sysChipSelect)) {
    // Falling edge of chip select, trigger a reset of the data FIFO first.
    // It's not on the rising edge, because some words might be making
    // their way through the request FIFO still.
    // Could we instead just ignore read requests if nCS is high?
    // I like the idea of nCS going low resetting stuff and putting everything into a good state though.
    regSysPendingReset := true.B
  }
  when (regSysPendingReset && !fifoResponse.io.writeResetBusy) {
    fifoResponse.io.reset := true.B
    regSysPendingReset := false.B
  }

  // Debug output
  io.debugRequestOverflow := XpmCdcSingle(spiClock, fifoRequestOverflow)
  io.debugResponseUnderflow := XpmCdcSingle(spiClock, fifoResponseUnderflow)
}
