/*
 * Copyright (c) bdew, 2014 - 2015
 * https://github.com/bdew/ae2stuff
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package net.bdew.ae2stuff.machines.wireless

import java.util
import appeng.api.AEApi
import appeng.api.implementations.tiles.IColorableTile
import appeng.api.networking.security.IActionHost
import appeng.api.networking.{GridFlags, IGridConnection, IGridNode}
import appeng.api.util.AEColor
import appeng.helpers.ICustomNameObject
import net.bdew.ae2stuff.AE2Stuff
import net.bdew.ae2stuff.grid.{GridTile, VariableIdlePower}
import net.bdew.lib.block.BlockRef
import net.bdew.lib.data.base.{TileDataSlots, UpdateKind}
import net.bdew.lib.multiblock.data.DataSlotPos
import net.minecraft.block.Block
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

class TileWireless extends TileWirelessBase {

  private val link =
    DataSlotPos("link", this).setUpdate(UpdateKind.SAVE, UpdateKind.WORLD)
  val maxConnections = 1

  override def canAddLink: Boolean = getAvailableConnections > 0
  override def getAvailableConnections: Int =
    maxConnections - connectionMap.size

  override def getFlags: util.EnumSet[GridFlags] =
    util.EnumSet.of(GridFlags.DENSE_CAPACITY)

//  serverTick.listen(() => {
//    if (connection == null && link.isDefined) {
//      try {
//        setupConnection()
//      } catch {
//        case t: Throwable =>
//          AE2Stuff.logWarnException(
//            "Failed setting up wireless link %s <-> %s: %s",
//            t,
//            myPos,
//            link.get,
//            t.getMessage
//          )
//          doUnlink()
//      }
//    }
//  })

  override def doLink(other: TileWirelessBase): Boolean = {
    if (!canAddLink || !other.canAddLink || isConnecterTo(other)) return false

    this.customName = other.customName
    setupConnection(other)
  }

  def doUnlink(): Unit = {
    breakConnection()
  }

  private def setupConnection(other: TileWirelessBase): Boolean = {
    val connection = Option(
      AEApi.instance().createGridConnection(this.getNode, other.getNode)
    ).getOrElse(return false)

    connectionMap.put(other, connection)
    other.connectionMap.put(this, connection)

    val dx = this.xCoord - other.xCoord
    val dy = this.yCoord - other.yCoord
    val dz = this.zCoord - other.zCoord
    // val power = cfg.powerBase + cfg.powerDistanceMultiplier * (dx * dx + dy * dy + dz * dz)
    val dist = math.sqrt(dx * dx + dy * dy + dz * dz)
    val power = cfg.powerBase + cfg.powerDistanceMultiplier * dist * math.log(
      dist * dist + 3
    )
    this.setIdlePowerUse(power)

    other.setIdlePowerUse(power)

    if (worldObj.blockExists(xCoord, yCoord, zCoord))
      worldObj.setBlockMetadataWithNotify(
        this.xCoord,
        this.yCoord,
        this.zCoord,
        1,
        3
      )
    if (worldObj.blockExists(other.xCoord, other.yCoord, other.zCoord)) {
      worldObj.setBlockMetadataWithNotify(
        other.xCoord,
        other.yCoord,
        other.zCoord,
        1,
        3
      )
    }
    true
  }

  private def breakConnection(): Unit = {
    getAllConnection.foreach(_.destroy())

    getConnectedTiles foreach { other =>
      other.setIdlePowerUse(0d)
      if (worldObj.blockExists(other.xCoord, other.yCoord, other.zCoord)) {
        worldObj.setBlockMetadataWithNotify(
          other.xCoord,
          other.yCoord,
          other.zCoord,
          0,
          3
        )
        connectionMap.remove(other)
        other.connectionMap.remove(this)
      }
    }
    setIdlePowerUse(0d)
    if (worldObj.blockExists(xCoord, yCoord, zCoord))
      worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, 0, 3)
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
