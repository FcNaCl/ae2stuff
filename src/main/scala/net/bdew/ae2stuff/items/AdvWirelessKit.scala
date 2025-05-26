/*
 * Copyright (c) bdew, 2014 - 2015
 * https://github.com/bdew/ae2stuff
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package net.bdew.ae2stuff.items

import appeng.api.config.SecurityPermissions
import appeng.api.exceptions.FailedConnection
import net.bdew.ae2stuff.AE2Stuff
import net.bdew.ae2stuff.grid.Security
import net.bdew.ae2stuff.machines.wireless.{BlockWireless, TileWireless}
import net.bdew.ae2stuff.misc.AdvItemLocationStore
import net.bdew.lib.Misc
import net.bdew.lib.block.BlockRef
import net.bdew.lib.items.SimpleItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.Vec3
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection
import net.bdew.lib.helpers.ChatHelper._

import java.util

object AdvWirelessKit
    extends SimpleItem("AdvWirelessKit")
    with AdvItemLocationStore {
  setMaxStackSize(1)

  private val MODE_QUEUING = false;
  private val MODE_BINDING = true;

  /** Determines the direction the player is looking based on their view vector.
    * The method calculates the dominant axis (X, Y, or Z) based on the view
    * vector's absolute values and resolves the corresponding orientation using
    * the helper method FindPlayerLookOrientation.
    *
    * @param player
    *   The player entity whose look direction is to be determined.
    * @return
    *   The determined ForgeDirection representing the player's look direction,
    *   which could be ForgeDirection.EAST, WEST, UP, DOWN, NORTH, or SOUTH.
    */
  private def FindPlayerLookDirection(player: EntityPlayer): ForgeDirection = {
    val view: Vec3 = player.getLookVec()
    val absX = Math.abs(view.xCoord)
    val absY = Math.abs(view.yCoord)
    val absZ = Math.abs(view.zCoord)
    if (absX > absY && absX > absZ) {
      return FindPlayerLookVectorOrientation(
        view.xCoord,
        ForgeDirection.EAST,
        ForgeDirection.WEST
      )
    } else if (absY > absX && absY > absZ) {
      return FindPlayerLookVectorOrientation(
        view.yCoord,
        ForgeDirection.UP,
        ForgeDirection.DOWN
      )
    } else {
      return FindPlayerLookVectorOrientation(
        view.zCoord,
        ForgeDirection.SOUTH,
        ForgeDirection.NORTH
      )
    }
  }

  /** Determines the player's vector orientation based on its value. Selects
    * between two specified directions depending on whether the coordinate is
    * positive or non-positive.
    *
    * @param coord
    *   A double representing the coordinate of the player's vector. If the
    *   value is positive, the method returns the first direction. Otherwise, it
    *   returns the second direction.
    * @param dir1
    *   The first possible direction.
    * @param dir2
    *   The second possible direction.
    * @return
    *   The determined ForgeDirection, either dir1 or dir2, based on the
    *   coordinate value.
    */
  private def FindPlayerLookVectorOrientation(
      coord: Double,
      dir1: ForgeDirection,
      dir2: ForgeDirection
  ): ForgeDirection = {
    if (coord > 0) {
      return dir1
    } else {
      return dir2
    }
  }

  private def clearQueue(stack: ItemStack, player: EntityPlayer) = {
    while (hasLocation(stack)) {
      popLocation(stack)
    }
    player.addChatMessage(
      L("ae2stuff.wireless.advtool.queueing.clear").setColor(
        Color.GREEN
      )
    )
    true
  }

  private def displayCurrentMode(stack: ItemStack, player: EntityPlayer) = {
    if (getMode(stack)) {
      player.addChatMessage(
        L("ae2stuff.wireless.advtool.binding.activated").setColor(
          Color.GREEN
        )
      )
    } else {
      player.addChatMessage(
        L("ae2stuff.wireless.advtool.queueing.activated").setColor(
          Color.GREEN
        )
      )
    }
  }

  private def checkSecurity(
      tile: TileWireless,
      player: EntityPlayer,
      pid: Integer
  ): Boolean = {
    if (
      !Security.playerHasPermission(
        tile.getNode.getGrid,
        pid,
        SecurityPermissions.BUILD
      )
    ) {
      player.addChatMessage(
        L("ae2stuff.wireless.tool.security.player").setColor(Color.RED)
      )
      return false
    }
    true
  }

  private def checkHubAvailability(
      player: EntityPlayer,
      tile: TileWireless
  ): Boolean = {
    if (tile.connectionsList.length >= 32) {
      player.addChatMessage(
        L("ae2stuff.wireless.tool.targethubfull").setColor(Color.RED)
      )
      return false
    }
    true
  }

  private def appEndQueue(
      tile: TileWireless,
      stack: ItemStack,
      pos: BlockRef,
      player: EntityPlayer,
      world: World
  ): Boolean = {

    val ctrlIsDown = AE2Stuff.keybindLCtrl.isKeyDown(player)
    val freeConnexions =
      if (ctrlIsDown && tile.isHub) 32 - tile.connectionsList.length else 1
    if (!checkHubAvailability(player, tile)) return false
    var i = 0
    var success = false
    while (i < freeConnexions) {
      success =
        addLocation(stack, pos, world.provider.dimensionId, isHub = true)
      i = i + 1
    }

    if (ctrlIsDown && success && tile.isHub) {
      player.addChatMessage(
        L(
          "ae2stuff.wireless.advtool.hub.queued",
          i.toString
        ).setColor(Color.GREEN)
      )
      return true
    }

    if (success) {
      player.addChatMessage(
        L(
          "ae2stuff.wireless.advtool.queued",
          pos.x.toString,
          pos.y.toString,
          pos.z.toString
        ).setColor(Color.GREEN)
      )
      return true
    }

    player.addChatMessage(
      L("ae2stuff.wireless.advtool.queuederror").setColor(Color.RED)
    )
    false
  }

  private def doBind(
      src: TileWireless,
      dst: TileWireless,
      player: EntityPlayer,
      pid: Integer
  ): Boolean = {
    // Player can modify both sides - unlink current connections if any
    if (!src.isHub) src.doUnlink()
    if (!dst.isHub) dst.doUnlink()

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
      target: TileWireless,
      stack: ItemStack,
      player: EntityPlayer,
      world: World
  ): Boolean = {
    val pid = Security.getPlayerId(player)

    if (!hasLocation(stack)) {
      player.addChatMessage(
        L("ae2stuff.wireless.advtool.noconnectors").setColor(Color.RED)
      )
      return true
    }

    if (getDimension(stack) != world.provider.dimensionId) {
      // Different dimensions - error out
      player.addChatMessage(
        L("ae2stuff.wireless.tool.dimension").setColor(Color.RED)
      )
      return true
    }

    if (!checkSecurity(target, player, pid)) {
      return true
    }

    if (target.connectionsList.length >= 32) {
      player.addChatMessage(
        L("ae2stuff.wireless.tool.targethubfull").setColor(Color.RED)
      )
      return true
    }

    val ctrlIsDown = AE2Stuff.keybindLCtrl.isKeyDown(player)
    val once = !(ctrlIsDown && target.isHub)
    val iterator = iterOnValidLocation(stack, world, target)
    iterator.foreach { tile =>
      // And check that the player can modify it too
      if (!checkSecurity(tile, player, pid)) {
        return true
      }

      if (tile.isHub && target.isHub) {
        player.addChatMessage(
          L("ae2stuff.wireless.tool.failed").setColor(Color.RED)
        )
        return false
      }

      doBind(tile, target, player, pid)

      if (once)
        return true
    }
    true
  }

  override def onItemRightClick(
      stack: ItemStack,
      world: World,
      player: EntityPlayer
  ): ItemStack = {
    if (world.isRemote || !player.isSneaking) {
      return stack
    }
    // Implies the player is both sneaking and pressing ctrl
    if (AE2Stuff.keybindLCtrl.isKeyDown(player)) {
      clearQueue(stack, player)
      return stack
    }
    // Implies the player is sneaking
    toggleMode(stack)
    displayCurrentMode(stack, player)
    stack
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
    if (world.isRemote) return true

    if (player.isSneaking) {
      if (AE2Stuff.keybindLCtrl.isKeyDown(player)) {
        return clearQueue(stack, player)
      }
      toggleMode(stack)
      displayCurrentMode(stack, player)
      return false
    }

    val pos = BlockRef(x, y, z)
    if (!pos.blockIs(world, BlockWireless)) return false
    val tile: TileWireless = pos.getTile[TileWireless](world).get
    if (tile == null) return false
    val pid = Security.getPlayerId(player)
    // Check that the player can modify the network
    if (!checkSecurity(tile, player, pid)) return false

    if (getMode(stack) == MODE_QUEUING) {
      appEndQueue(tile, stack, pos, player, world)
    } else {
      bindWireless(tile, stack, player, world)
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
    if (getLocations(stack).tagCount() > 0) {
      val next = getNextLocation(stack)
      list.add(
        Misc.toLocalF(
          "ae2stuff.wireless.advtool.connector.next",
          next.x,
          next.y,
          next.z
        )
      )
    }
    if (getMode(stack) == MODE_QUEUING) {
      list.add(Misc.toLocal("ae2stuff.wireless.advtool.queueing"))
      if (getLocations(stack).tagCount() == 0) {
        list.add(Misc.toLocal("ae2stuff.wireless.advtool.queueing.empty"))
      } else {
        list.add(Misc.toLocal("ae2stuff.wireless.advtool.queueing.notempty"))
        for (i <- 0 until getLocations(stack).tagCount()) {
          val loc = BlockRef.fromNBT(getLocations(stack).getCompoundTagAt(i))
          list.add(loc.x + "," + loc.y + "," + loc.z)
        }
      }
      list.add(
        Misc.toLocal("ae2stuff.wireless.tooltips.advtool.hubqols.queueing")
      )
    } else if (getMode(stack) == MODE_BINDING) {
      list.add(Misc.toLocal("ae2stuff.wireless.advtool.binding"))
      if (getLocations(stack).tagCount() == 0) {
        list.add(Misc.toLocal("ae2stuff.wireless.advtool.binding.empty"))
      } else {
        list.add(Misc.toLocal("ae2stuff.wireless.advtool.binding.notempty"))
        for (i <- 0 until getLocations(stack).tagCount()) {
          val loc = BlockRef.fromNBT(getLocations(stack).getCompoundTagAt(i))
          list.add(loc.x + "," + loc.y + "," + loc.z)
        }
      }
      list.add(
        Misc.toLocal("ae2stuff.wireless.tooltips.advtool.hubqols.binding")
      )
    }
    list.add(Misc.toLocal("ae2stuff.wireless.tooltips.advtool.queueing.clear"))
    list.add(Misc.toLocal("ae2stuff.wireless.advtool.extra"))
  }
}
