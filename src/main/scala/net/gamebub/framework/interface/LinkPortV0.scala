package net.gamebub.framework.interface

import chisel3._

class LinkPortV0(
) extends Bundle {
  val soIn = Input(Bool())
  val siIn = Input(Bool())
  val sdIn = Input(Bool())
  val scIn = Input(Bool())
  val soOut = Output(Bool())
  val siOut = Output(Bool())
  val sdOut = Output(Bool())
  val scOut = Output(Bool())
  val soDir = Output(Bool())
  val siDir = Output(Bool())
  val sdDir = Output(Bool())
  val scDir = Output(Bool())
}
