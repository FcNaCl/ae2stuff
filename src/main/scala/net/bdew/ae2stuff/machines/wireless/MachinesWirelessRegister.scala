/*
 * Copyright (c) bdew, 2014 - 2015
 * https://github.com/bdew/ae2stuff
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package net.bdew.ae2stuff.machines.wireless

import net.bdew.ae2stuff.machines.wireless.hub.{
  BlockWirelessHub,
  TileWirelessHub
}
import net.bdew.ae2stuff.machines.wireless.simple.{BlockWireless, TileWireless}
import net.bdew.lib.machine.Machine
import net.minecraft.block.Block

class MachineWireless[T <: Block](name: String, blockConstruct: => T)
    extends Machine(name, blockConstruct) {
  lazy val powerBase = tuning.getDouble("PowerBase")
  lazy val powerDistanceMultiplier = tuning.getDouble("PowerDistanceMultiplier")
}

object MachinesWirelessRegister {
  val wirelessHub = new MachineWireless("wirelessHub", BlockWirelessHub)
  val wireless = new MachineWireless("wireless", BlockWireless)

  def get(
      tileWirelessBase: TileWirelessBase
  ): Option[MachineWireless[_ <: Block]] = {
    tileWirelessBase match {
      case _: TileWirelessHub => Some(wirelessHub)
      case _: TileWireless    => Some(wireless)
      case _                  => None
    }
  }

  def getAllMachines: Seq[MachineWireless[_ <: Block]] = {
    Seq(wirelessHub, wireless)
  }
}
