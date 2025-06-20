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
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.relauncher.{Side, SideOnly}
import net.bdew.ae2stuff.AE2Stuff
import net.bdew.ae2stuff.grid.Security
import net.bdew.ae2stuff.items.WirelessKitHelper.{
  checkBindingValidity,
  checkSecurity,
  doBind
}
import net.bdew.ae2stuff.machines.wireless.hub.TileWirelessHub
import net.bdew.ae2stuff.machines.wireless.TileWirelessBase
import net.bdew.ae2stuff.machines.wireless.simple.BlockWireless
import net.bdew.ae2stuff.misc.{AdvItemLocationStore, WirelessKitModes}
import net.bdew.lib.Misc
import net.bdew.lib.block.BlockRef
import net.bdew.lib.items.SimpleItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.{IIcon, Vec3}
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection
import net.bdew.lib.helpers.ChatHelper._
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.client.resources.I18n
import net.minecraft.client.settings.KeyBinding
import net.minecraft.client.Minecraft
import org.lwjgl.input.Keyboard

import java.util

object AdvWirelessKit
    extends SimpleItem("AdvWirelessKit")
    with AdvItemLocationStore {
  setMaxStackSize(1)

  private var queueIcon: IIcon = null
  private var bindIcon: IIcon = null

  @SideOnly(Side.CLIENT)
  override def registerIcons(reg: IIconRegister) {
    queueIcon = reg.registerIcon(Misc.iconName(modId, name))
    itemIcon = queueIcon
    bindIcon = reg.registerIcon(Misc.iconName(modId, name + "-binding"))
  }

  override def getIcon(stack: ItemStack, pass: Int): IIcon = {
    getMode(stack) match {
      case _: WirelessKitModes.QUEUING =>
        itemIcon = queueIcon
        queueIcon
      case _: WirelessKitModes.BINDING =>
        itemIcon = bindIcon
        bindIcon
    }
  }

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
  private def FindPlayerLookVectorDirection(
      player: EntityPlayer
  ): ForgeDirection = {
    val view: Vec3 = player.getLookVec()
    val absX = Math.abs(view.xCoord)
    val absY = Math.abs(view.yCoord)
    val absZ = Math.abs(view.zCoord)
    if (absX > absY && absX > absZ) {
      FindPlayerLookVectorOrientation(
        view.xCoord,
        ForgeDirection.EAST,
        ForgeDirection.WEST
      )
    } else if (absY > absX && absY > absZ) {
      FindPlayerLookVectorOrientation(
        view.yCoord,
        ForgeDirection.UP,
        ForgeDirection.DOWN
      )
    } else {
      FindPlayerLookVectorOrientation(
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
      dir1
    } else {
      dir2
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
    getMode(stack) match {
      case _: WirelessKitModes.QUEUING =>
        player.addChatMessage(
          L("ae2stuff.wireless.advtool.queueing.activated").setColor(
            Color.GREEN
          )
        )
      case _: WirelessKitModes.BINDING =>
        player.addChatMessage(
          L("ae2stuff.wireless.advtool.binding.activated").setColor(Color.GREEN)
        )
    }
  }

  private def checkHubAvailability(
      player: EntityPlayer,
      tile: TileWirelessBase
  ): Boolean = {
    if (tile.isHub && tile.canAddLink)
      return true
    player.addChatMessage(
      L("ae2stuff.wireless.tool.targethubfull").setColor(Color.RED)
    )
    false
  }

  private def appEndQueue(
      tile: TileWirelessBase,
      stack: ItemStack,
      pos: BlockRef,
      player: EntityPlayer,
      world: World
  ): Boolean = {

    val ctrlIsDown = AE2Stuff.keybindModeSwitch.isKeyDown(player)
    val freeConnexions = if (ctrlIsDown) tile.getAvailableConnections else 1
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

  def appEndQueueLine(): Boolean = {
    return false // No line queueing
  }

  private def bindWireless(
      target: TileWirelessBase,
      stack: ItemStack,
      player: EntityPlayer,
      world: World
  ): Boolean = {

    val pid = Security.getPlayerId(player)
    if (!checkBindingValidity(stack, target, player, pid)) return true

    val ctrlIsDown = AE2Stuff.keybindModeSwitch.isKeyDown(player)
    val once = !ctrlIsDown
    val iterator = iterOnValidLocation(stack, world, target)
    iterator.foreach { tile =>
      // And check that the player can modify it too
      if (!checkSecurity(tile, player, pid)) {
        return true
      }

//      if (tile.isHub && target.isHub) {
//        player.addChatMessage(
//          L("ae2stuff.wireless.tool.failed").setColor(Color.RED)
//        )
//        return true
//      }

      doBind(tile, target, player, pid)

      if (once)
        return true
    }
    true
  }

  private def bindWirelessLine(
      target: TileWirelessBase,
      stack: ItemStack,
      player: EntityPlayer,
      world: World
  ): Boolean = {
    var _target: TileWirelessBase = target

    val pid = Security.getPlayerId(player)
    if (!checkBindingValidity(stack, _target, player, pid)) return true

    var x: Int = _target.xCoord
    var y: Int = _target.yCoord
    var z: Int = _target.zCoord

    val direction = FindPlayerLookVectorDirection(player)

    val iterator = iterOnValidLocation(stack, world, _target)
    iterator.foreach { tile =>
      // And check that the player can modify it too
      if (!checkSecurity(tile, player, pid)) {
        return true
      }

//      if (tile.isHub && _target.isHub) {
//        player.addChatMessage(
//          L("ae2stuff.wireless.tool.failed").setColor(Color.RED)
//        )
//        return true
//      }

      // bind the selected wireless in queue and the target
      if (doBind(tile, _target, player, pid)) {
        player.addChatMessage(
          L("ae2stuff.wireless.tool.failed").setColor(Color.RED)
        )
      }
      direction match {
        case ForgeDirection.UP    => y = y + 1
        case ForgeDirection.DOWN  => y = y - 1
        case ForgeDirection.EAST  => x = x - 1
        case ForgeDirection.WEST  => x = x + 1
        case ForgeDirection.NORTH => z = z - 1
        case ForgeDirection.SOUTH => z = z + 1
        case _                    => return true
      }
      _target = BlockRef(x, y, z)
        .getTile[TileWirelessBase](world)
        .getOrElse(return true)
    }
    true
  }

  override def onItemRightClick(
      stack: ItemStack,
      world: World,
      player: EntityPlayer
  ): ItemStack = {
    if (world.isRemote) return stack

    if (!player.isSneaking) {
      return stack
    }

    // Implies the player is both sneaking and pressing ctrl
    if (AE2Stuff.keybindModeSwitch.isKeyDown(player)) {
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
      if (AE2Stuff.keybindModeSwitch.isKeyDown(player)) {
        return clearQueue(stack, player)
      }
      toggleMode(stack)
      displayCurrentMode(stack, player)
      return false
    }

    val pos = BlockRef(x, y, z)
    val tile: TileWirelessBase = pos.getTile[TileWirelessBase](world).get
    if (tile == null) return false
    val pid = Security.getPlayerId(player)
    // Check that the player can modify the network
    if (!checkSecurity(tile, player, pid)) return false

    getMode(stack) match {
      case WirelessKitModes.MODE_BINDING =>
        bindWireless(tile, stack, player, world)
      case WirelessKitModes.MODE_BINDING_LINE =>
        bindWirelessLine(tile, stack, player, world)
      case WirelessKitModes.MODE_QUEUING =>
        appEndQueue(tile, stack, pos, player, world)
      case WirelessKitModes.MODE_QUEUING_LINE =>
        appEndQueueLine()
      case _ => true // Should not happen
    }
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

    getMode(stack) match {
      case _: WirelessKitModes.QUEUING =>
        list.add(Misc.toLocal("ae2stuff.wireless.advtool.queueing"))
        getLocations(stack).tagCount() match {
          case 0 =>
            list.add(Misc.toLocal("ae2stuff.wireless.advtool.queueing.empty"))
          case _ =>
            list.add(
              Misc.toLocal("ae2stuff.wireless.advtool.queueing.notempty")
            )
            for (i <- 0 until getLocations(stack).tagCount()) {
              val loc =
                BlockRef.fromNBT(getLocations(stack).getCompoundTagAt(i))
              list.add(loc.x + "," + loc.y + "," + loc.z)
            }
        }
        list.add(
          I18n.format(
            "ae2stuff.wireless.tooltips.advtool.hubqols.queueing",
            Minecraft.getMinecraft.gameSettings.keyBindings
              .find(_.getKeyDescription == AE2Stuff.keybindModeId)
              .map(kb => Keyboard.getKeyName(kb.getKeyCode))
              .getOrElse("NONE")
          )
        )

      case _: WirelessKitModes.BINDING =>
        list.add(Misc.toLocal("ae2stuff.wireless.advtool.binding"))
        getLocations(stack).tagCount() match {
          case 0 =>
            list.add(Misc.toLocal("ae2stuff.wireless.advtool.binding.empty"))
          case _ =>
            list.add(Misc.toLocal("ae2stuff.wireless.advtool.binding.notempty"))
            for (i <- 0 until getLocations(stack).tagCount()) {
              val loc =
                BlockRef.fromNBT(getLocations(stack).getCompoundTagAt(i))
              list.add(loc.x + "," + loc.y + "," + loc.z)
            }
        }
        list.add(
          I18n.format(
            "ae2stuff.wireless.tooltips.advtool.hubqols.binding",
            Minecraft.getMinecraft.gameSettings.keyBindings
              .find(_.getKeyDescription == AE2Stuff.keybindModeId)
              .map(kb => Keyboard.getKeyName(kb.getKeyCode))
              .getOrElse("NONE")
          )
        )
      case _ =>
    }
    list.add(
      I18n.format(
        "ae2stuff.wireless.tooltips.advtool.queueing.clear",
        Minecraft.getMinecraft.gameSettings.keyBindings
          .find(_.getKeyDescription == AE2Stuff.keybindModeId)
          .map(kb => Keyboard.getKeyName(kb.getKeyCode))
          .getOrElse("NONE")
      )
    )
    list.add(
      I18n.format(
        "ae2stuff.wireless.tooltips.advtool.linemode",
        Minecraft.getMinecraft.gameSettings.keyBindings
          .find(_.getKeyDescription == AE2Stuff.keybindLineModeId)
          .map(kb => Keyboard.getKeyName(kb.getKeyCode))
          .getOrElse("NONE")
      )
    )
    list.add(Misc.toLocal("ae2stuff.wireless.advtool.extra"))
  }
}
