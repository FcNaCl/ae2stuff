package net.bdew.ae2stuff.machines.wireless
import appeng.api.networking.IGridNode
import appeng.api.util.AEColor
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

import java.util

class TileWirelessHub extends TileWirelessBase {

  val maxConnections = 32

  var hubPowerUsage = 0d

  def setHubPowerUse(power: Double): Unit = {
    hubPowerUsage += power
    this.setIdlePowerUse(hubPowerUsage)
  }

  def getHubChannels: Int = {
    var channels = 0
    getAllConnection foreach { that =>
      channels += that.getUsedChannels
    }
    channels
  }

  override def canAddLink: Boolean = ???

  override def doLink(other: TileWirelessBase): Boolean = ???

  override def doUnlink(): Unit = ???

  override def getMachineRepresentation: ItemStack = ???

  override def getActionableNode: IGridNode = ???

  override def getColor: AEColor = ???

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
      stack.setItemDamage(this.color.ordinal() + 18)
    } else {
      stack.setItemDamage(17)
    }
    drops.add(stack)
    drops
  }

  override def getAvailableConnections: Int = ???
}
