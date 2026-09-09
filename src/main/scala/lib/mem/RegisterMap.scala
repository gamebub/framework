package lib.mem

import chisel3._

/**
 * Exposes a simple map of registers as a MemoryInterface.
 *
 * Only supports combinational reads and synchronous, single-cycle writes.
 *
 * writeStrobe is ignored.
 */
object RegisterMap {
  def apply (addressWidth: Int, dataWidth: Int, entries: Seq[(Int, Entry)]): MemoryInterface = {
    val byteWidth = dataWidth / 8

    // Ensure registers are not too big.
    for ((reg, i) <- entries.map(_._2).zipWithIndex) {
      if (reg.width > dataWidth) {
        throw new IllegalArgumentException(f"entry $i (width ${reg.width}) is larger than data width $dataWidth")
      }
    }
    // Ensure addresses are word-aligned and in bounds.
    for ((addr, i) <- entries.map(_._1).zipWithIndex) {
      if (addr % byteWidth != 0) {
        throw new IllegalArgumentException(f"entry $i (at 0x$addr%x) is not aligned to $byteWidth bytes")
      }
      if (addr >= (1 << addressWidth)) {
        throw new IllegalArgumentException(f"entry $i (at 0x$addr%x) is larger than address width $addressWidth")
      }
    }
    // Ensure addresses are unique.
    entries.map(_._1).groupBy(identity).collect { case (x, List(_, _, _*)) => x }.foreach(addr => {
      throw new IllegalArgumentException(f"address 0x$addr%x is used multiple times")
    })


    val interface = Wire(new MemoryInterface(addressWidth, dataWidth))

    interface.dataRead := 0.U
    interface.done := true.B

    entries.foreach { case (address, reg) =>
      when (interface.enable && interface.address === address.U) {
        val regReadData = reg.read.fn(!interface.write)
        reg.write.fn(interface.write, interface.dataWrite)

        when (!interface.write) {
          interface.dataRead := regReadData
        }
      }
    }

    interface
  }

  case class ReadFn(fn: Bool => UInt)
  object ReadFn {
    // Simple read from a register or wire.
    def apply(reg: Data): ReadFn = ReadFn(_ => reg.asUInt)

    // No-op read, returns 0.
    def apply(): ReadFn = ReadFn(_ => 0.U)
  }

  case class WriteFn(fn: (Bool, UInt) => Unit)
  object WriteFn {
    // Simple write to a register.
    def apply(reg: Data): WriteFn = WriteFn((writeEnable, writeData) => {
      when (writeEnable) {
        reg := writeData.asTypeOf(reg)
      }
    })

    // No-op write.
    def apply(): WriteFn = WriteFn((_, _) => ())
  }

  case class Entry(width: Int, read: ReadFn, write: WriteFn)
  object Entry {
    def r(reg: Data): Entry = Entry(reg.getWidth, ReadFn(reg), WriteFn())

    def w(reg: Data): Entry = Entry(reg.getWidth, ReadFn(), WriteFn(reg))

    def rw(reg: Data): Entry = Entry(reg.getWidth, ReadFn(reg), WriteFn(reg))
  }
}
