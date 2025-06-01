/*
 * Copyright (c) bdew, 2014 - 2015
 * https://github.com/bdew/ae2stuff
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package net.bdew.ae2stuff.machines.wireless.simple

import appeng.api.AEApi
import appeng.api.networking.GridFlags
import appeng.api.util.AEColor
import net.bdew.ae2stuff.machines.wireless.TileWirelessBase
import net.bdew.lib.data.base.UpdateKind
import net.bdew.lib.multiblock.data.DataSlotPos
import net.minecraft.item.ItemStack
import net.minecraft.world.World

import java.util

class TileWireless extends TileWirelessBase {

  override val maxConnections = 1

  override def doLink(other: TileWirelessBase): Boolean = {
    doUnlink()
    this.customName = other.customName
    setupConnection(other)
  }

  def doUnlink(): Unit = {
    breakAllConnection()
  }

  override def getDrops(
      world: World,
      x: Int,
      y: Int,
      z: Int,
      metadata: Int,
      fortune: Int,
      drops: util.ArrayList[ItemStack]
  ): util.ArrayList[ItemStack] = {
    val stack = new ItemStack(BlockWireless)
    if (this.color != AEColor.Transparent) {
      stack.setItemDamage(this.color.ordinal() + 1)
    }
    drops.add(stack)
    drops
  }
}
