package net.gamebub.framework.interface

import chisel3._

/**
  * Cartridge port interface.
  * 
  * Bank 0: A16 to A23
  * Bank 1: AD8 to AD15
  * Bank 2: AD0 to AD7
  * Bank 3:
  *  0: nCS1
  *  1: nRD
  *  2: nWR
  *  3: PHI
  * Pin 30: nRST (GB) / nCS2 (GBA)
  * Pin 31: VIN (GB) / nIRQ (GBA)
  *
  * Directions are all 1 for output, 0 for input.
  */
class CartridgePortV0(
) extends Bundle {
  val enabled = Output(Bool())

  /** Cartridge switch: 1 when DMG/CGB cartridge inserted */
  val switch = Input(Bool())

  val bank0In = Input(UInt(8.W))
  val bank1In = Input(UInt(8.W))
  val bank2In = Input(UInt(8.W))
  val bank3In = Input(UInt(4.W))
  val pin30In = Input(Bool())
  val pin31In = Input(Bool())

  val bank0Out = Output(UInt(8.W))
  val bank1Out = Output(UInt(8.W))
  val bank2Out = Output(UInt(8.W))
  val bank3Out = Output(UInt(4.W))
  val pin30Out = Output(Bool())
  val pin31Out = Output(Bool())

  val bank0Dir = Output(Bool())
  val bank1Dir = Output(Bool())
  val bank2Dir = Output(Bool())
  val bank3Dir = Output(Bool())
  val pin30Dir = Output(Bool())
  val pin31Dir = Output(Bool())
}
