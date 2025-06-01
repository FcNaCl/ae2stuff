package net.bdew.ae2stuff.machines.wireless.hub

import appeng.api.networking.IGridNode
import appeng.api.util.AEColor
import net.bdew.ae2stuff.machines.wireless.TileWirelessBase
import net.bdew.ae2stuff.machines.wireless.simple.BlockWireless
import net.minecraft.item.ItemStack
import net.minecraft.world.World

import java.util

class TileWirelessHub extends TileWirelessBase {

  val maxConnections = 32

  override def doLink(other: TileWirelessBase): Boolean = {
    if (!other.canAddLink && !canAddLink) return false
    this.customName = other.customName
    setupConnection(other)
  }

  override def doUnlink(): Unit = {
    breakAllConnection()
  }

  override def doUnlink(other: TileWirelessBase): Unit = breakConnection(other)

  override def getDrops(
      world: World,
      x: Int,
      y: Int,
      z: Int,
      metadata: Int,
      fortune: Int,
      drops: util.ArrayList[ItemStack]
  ): util.ArrayList[ItemStack] = {
    val stack = new ItemStack(BlockWirelessHub)
    if (this.color != AEColor.Transparent) {
      stack.setItemDamage(this.color.ordinal() + 18)
    } else {
      stack.setItemDamage(17)
    }
    drops.add(stack)
    drops
  }
}
