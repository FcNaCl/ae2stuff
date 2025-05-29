/*
 * Copyright (c) bdew, 2014 - 2015
 * https://github.com/bdew/ae2stuff
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package net.bdew.ae2stuff.grid

import java.util

import appeng.api.AEApi
import appeng.api.networking._
import appeng.api.networking.events.MENetworkEvent
import appeng.api.util.{AECableType, AEColor, DimensionalCoord}
import appeng.me.GridAccessException
import cpw.mods.fml.common.FMLCommonHandler
import net.bdew.lib.items.ItemUtils
import net.bdew.lib.tile.TileExtended
import net.bdew.lib.tile.inventory.BreakableInventoryTile
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraftforge.common.util.ForgeDirection

trait GridTile extends TileExtended with IGridHost with IGridBlock {
  var node: IGridNode = null
  var initialized = false

  var placingPlayer: EntityPlayer = null

  serverTick.listen(() => {
    if (!initialized) {
      if (placingPlayer != null)
        getNode.setPlayerID(Security.getPlayerId(placingPlayer.getGameProfile))
      getNode.updateState()
      initialized = true
    }
  })

  persistSave.listen(tag => {
    if (node != null)
      node.saveToNBT("ae_node", tag)
  })

  persistLoad.listen(tag => {
    if (FMLCommonHandler.instance().getEffectiveSide.isServer) {
      unRegisterNode()
      node = AEApi.instance().createGridNode(this)
      if (tag.hasKey("ae_node"))
        node.loadFromNBT("ae_node", tag)
    }
    initialized = false
  })

  def unRegisterNode(): Unit = {
    if (node != null) {
      node.destroy()
      node = null
      initialized = false
    }
  }

  override def invalidate(): Unit = {
    unRegisterNode()
    super.invalidate()
  }

  override def onChunkUnload(): Unit = {
    unRegisterNode()
    super.onChunkUnload()
  }

  def getNode: IGridNode = {
    if (getWorldObj == null || getWorldObj.isRemote) return null

    if (node == null)
      node = AEApi.instance().createGridNode(this)
    node
  }

  def safePostEvent(ev: MENetworkEvent) = {
    try {
      getNode.getGrid.postEvent(ev)
      true
    } catch {
      case t: GridAccessException => false
    }
  }

  // IGridHost
  override def getGridNode(p1: ForgeDirection) = getNode
  override def getCableConnectionType(p1: ForgeDirection) = AECableType.COVERED
  override def securityBreak() = {
    ItemUtils.throwItemAt(
      getWorldObj,
      xCoord,
      yCoord,
      zCoord,
      new ItemStack(getBlockType)
    )
    if (this.isInstanceOf[BreakableInventoryTile])
      this.asInstanceOf[BreakableInventoryTile].dropItems()
    getWorldObj.setBlockToAir(xCoord, yCoord, zCoord)
  }

  // IGridBlock
  override def getIdlePowerUsage = 0
  override def getFlags = util.EnumSet.noneOf(classOf[GridFlags])
  override def getGridColor = AEColor.Transparent
  override def getConnectableSides = util.EnumSet.allOf(classOf[ForgeDirection])
  override def getMachine = this
  override def isWorldAccessible = true
  override def getLocation = new DimensionalCoord(this)

  // Needs to be implemented by subclass
  override def getMachineRepresentation: ItemStack

  // Default notifications do nothing
  override def onGridNotification(p1: GridNotification): Unit = {}
  override def setNetworkStatus(p1: IGrid, p2: Int): Unit = {}
  override def gridChanged(): Unit = {}
}
