package lib.mem

import chisel3._
import chisel3.util._

/**
 * Multiplexes a MemoryInterface into several sub-interfaces, based on prefix matching
 * on the highest bits of the address.
 */
object MemoryMap {
  def apply (
    addressWidth: Int,
    dataWidth: Int,
    entries: Seq[(UInt, MemoryInterface)],
    default: Option[MemoryInterface] = None,
  ): MemoryInterface = {
    val interface = Wire(new MemoryInterface(addressWidth, dataWidth))

    for ((x, i) <- entries.map(_._1).zipWithIndex) {
      if (x.getWidth == 0) {
        throw new IllegalArgumentException(s"entry $i has invalid width 0")
      }
    }
    for ((x, i) <- entries.map(_._1).zipWithIndex) {
      for ((y, j) <- entries.map(_._1).zipWithIndex) {
        if (i != j) {
          val length = x.getWidth.min(y.getWidth)
          if (x.head(length) == y.head(length)) {
            throw new IllegalArgumentException(s"entry $j ($y) and $i ($x) have common prefix")
          }
        }
      }
    }

    val useDefault = WireDefault(true.B)

    // This will only take effect if none of the prefixes are matched.
    interface.dataRead := 0.U
    interface.done := true.B

    entries.foreach { case (prefix, mem) =>
      mem.address := interface.address
      mem.dataWrite := interface.dataWrite
      mem.writeStrobe := interface.writeStrobe

      when (interface.address.head(prefix.getWidth) === prefix) {
        mem.enable := interface.enable
        mem.write := interface.write
        interface.dataRead := mem.dataRead
        interface.done := mem.done
        useDefault := false.B
      } .otherwise {
        mem.enable := false.B
        mem.write := false.B
      }
    }

    default.foreach(mem => {
      mem.address := interface.address
      mem.dataWrite := interface.dataWrite
      mem.writeStrobe := interface.writeStrobe
      mem.enable := false.B
      mem.write := false.B

      when (useDefault) {
        mem.enable := interface.enable
        mem.write := interface.write
        interface.dataRead := mem.dataRead
        interface.done := mem.done
      }
    })
    
    interface
  }
}
