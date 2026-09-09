package net.gamebub.framework

import chisel3._
import chisel3.reflect.DataMirror
import net.gamebub.framework.interface._

trait Core extends Module {
  final def getInterface(name: String): Option[Data] = {
    val io = DataMirror.modulePorts(this).find((x) => x._1 == "io").map(_._2) match {
      case Some(io: Bundle) => io;
      case Some(_) => throw new CoreException("Core 'io' port must be Bundle");
      case None => throw new CoreException("Core missing 'io' port");
    }
    io.elements.get(name)
  }

  def bindExtModule[T <: Bundle](
    extModuleName: String,
    io: T,
    params: Map[String, Param] = Map.empty[String, Param],
  ) = {
    ExtModuleUtils.bindExtModule(extModuleName, io, params)
  }
}

class CoreException(message: String) extends RuntimeException(message)
