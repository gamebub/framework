package lib.util

import chisel3._
import chisel3.util._

/** Fixed-point fractional divider: generates pulses at the target frequency **/
class FractionalDivider(
    inputHz: Int,
    targetHz: Int,
) extends Module {
    assert(targetHz < inputHz)

    val io = IO(new Bundle {
        val pulse = Output(Bool())
    })

    val errorWidth = log2Ceil(inputHz + targetHz + 1)
    val error = RegInit(0.U(errorWidth.W))
    val nextError = error + targetHz.U

    when (nextError >= inputHz.U) {
        error := nextError - inputHz.U
        io.pulse := true.B
    } .otherwise {
        error := nextError
        io.pulse := false.B
    }
}