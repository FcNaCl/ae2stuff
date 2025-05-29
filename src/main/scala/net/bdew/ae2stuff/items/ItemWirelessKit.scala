/*
 * Copyright (c) bdew, 2014 - 2015
 * https://github.com/bdew/ae2stuff
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package net.bdew.ae2stuff.items

import java.util
import appeng.api.config.SecurityPermissions
import appeng.api.exceptions.FailedConnection
import net.bdew.ae2stuff.AE2Stuff
import net.bdew.ae2stuff.grid.Security

import net.bdew.ae2stuff.machines.wireless.{BlockWireless, TileWirelessBase}
import net.bdew.ae2stuff.misc.ItemLocationStore
import net.bdew.lib.Misc
import net.bdew.lib.block.BlockRef
import net.bdew.lib.items.SimpleItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.bdew.lib.helpers.ChatHelper._

object ItemWirelessKit
    extends SimpleItem("WirelessKit")
    with ItemLocationStore {
  setMaxStackSize(1)

  private def checkSecurity(
      t1: TileWirelessBase,
      p: EntityPlayer,
      pid: Int
  ) = {
    Security.playerHasPermission(
      t1.getNode.getGrid,
      pid,
      SecurityPermissions.BUILD
    )
  }

  private def appEndQueue(
      target: TileWirelessBase,
      stack: ItemStack,
      pos: BlockRef,
      player: EntityPlayer,
      world: World
  ) = {
    // Have no location stored - store current location
    player.addChatMessage(
      L(
        "ae2stuff.wireless.tool.bound1",
        pos.x.toString,
        pos.y.toString,
        pos.z.toString
      ).setColor(Color.GREEN)
    )
    setLocation(stack, pos, world.provider.dimensionId)
  }

  def checkBindingValidity(
      stack: ItemStack,
      target: TileWirelessBase,
      player: EntityPlayer,
      pid: Integer
  ): Boolean = {
    if (!hasLocation(stack)) {
      player.addChatMessage(
        L("ae2stuff.wireless.advtool.noconnectors").setColor(Color.RED)
      )
      return false
    }

    if (getDimension(stack) != player.worldObj.provider.dimensionId) {
      // Different dimensions - error out
      player.addChatMessage(
        L("ae2stuff.wireless.tool.dimension").setColor(Color.RED)
      )
      return false
    }

    if (!checkSecurity(target, player, pid)) {
      return false
    }

    if (!target.canAddLink) {
      player.addChatMessage(
        L("ae2stuff.wireless.tool.targethubfull").setColor(Color.RED)
      )
      return false
    }
    true
  }

  private def doBind(
      src: TileWirelessBase,
      dst: TileWirelessBase,
      player: EntityPlayer,
      pid: Integer
  ): Boolean = {
    // Player can modify both sides - unlink current connections if any
    if (!src.canAddLink) src.doUnlink()
    if (!src.canAddLink) dst.doUnlink()

    // Make player the owner of both blocks
    src.getNode.setPlayerID(pid)
    dst.getNode.setPlayerID(pid)
    try {
      if (src.doLink(dst)) {
        player.addChatMessage(
          L(
            "ae2stuff.wireless.tool.connected",
            src.xCoord.toString,
            src.yCoord.toString,
            src.zCoord.toString
          ).setColor(Color.GREEN)
        )
        return true
      } else {
        player.addChatMessage(
          L("ae2stuff.wireless.tool.failed").setColor(
            Color.RED
          )
        )
      }
    } catch {
      case e: FailedConnection =>
        player.addChatComponentMessage(
          (L(
            "ae2stuff.wireless.tool.failed"
          ) & ": " & e.getMessage).setColor(Color.RED)
        )
        dst.doUnlink()
        print("Failed to link wireless connector: " + e)
    }
    false
  }

  private def bindWireless(
      target: TileWirelessBase,
      stack: ItemStack,
      player: EntityPlayer,
      world: World
  ): Boolean = {

    val pid = Security.getPlayerId(player)
    if (!checkBindingValidity(stack, target, player, pid)) return true

    val tile =
      getLocation(stack).getTile[TileWirelessBase](world).getOrElse(return true)

    // And check that the player can modify it too
    if (!checkSecurity(tile, player, pid)) {
      return true
    }

    if (doBind(tile, target, player, pid))
      player.addChatMessage(
        L("ae2stuff.wireless.tool.failed").setColor(Color.RED)
      )
    true
  }

  override def onItemUse(
      stack: ItemStack,
      player: EntityPlayer,
      world: World,
      x: Int,
      y: Int,
      z: Int,
      side: Int,
      xOff: Float,
      yOff: Float,
      zOff: Float
  ): Boolean = {

    val pos = BlockRef(x, y, z)
    if (!pos.blockIs(world, BlockWireless)) return false

    if (!world.isRemote) return true

    val tile = pos.getTile[TileWirelessBase](world).getOrElse(return true)

    val pid = Security.getPlayerId(player)

    if (!checkSecurity(tile, player, pid)) {
      player.addChatMessage(
        L("ae2stuff.wireless.tool.security.player").setColor(Color.RED)
      )
      return true
    }

    hasLocation(stack) match {
      case true =>
        bindWireless(tile, stack, player, world)
      case false =>
        appEndQueue(tile, stack, pos, player, world)
    }
    true
  }

  override def addInformation(
      stack: ItemStack,
      player: EntityPlayer,
      tips: util.List[_],
      detailed: Boolean
  ): Unit = {
    val list = tips.asInstanceOf[util.List[String]]
    if (hasLocation(stack)) {
      val pos = getLocation(stack)
      list.add(
        Misc.toLocalF("ae2stuff.wireless.tool.bound1", pos.x, pos.y, pos.z)
      )
      list.add(Misc.toLocal("ae2stuff.wireless.tool.bound2"))
    } else {
      list.add(Misc.toLocal("ae2stuff.wireless.tool.empty"))
    }
  }
}
