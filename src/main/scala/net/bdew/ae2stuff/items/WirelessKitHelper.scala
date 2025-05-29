package net.bdew.ae2stuff.items

import appeng.api.config.SecurityPermissions
import appeng.api.exceptions.FailedConnection
import net.bdew.ae2stuff.grid.Security
import net.bdew.ae2stuff.items.ItemWirelessKit.{getDimension, hasLocation}
import net.bdew.ae2stuff.machines.wireless.TileWirelessBase
import net.bdew.lib.helpers.ChatHelper._
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack

object WirelessKitHelper {
  def checkSecurity(
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

  def doBind(
      src: TileWirelessBase,
      dst: TileWirelessBase,
      player: EntityPlayer,
      pid: Integer
  ): Boolean = {
    // Player can modify both sides - unlink current connections if any
    if (!src.isHub) src.doUnlink()
    if (!src.isHub) dst.doUnlink()

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

    if (target.isHub && !target.canAddLink) {
      player.addChatMessage(
        L("ae2stuff.wireless.tool.targethubfull").setColor(Color.RED)
      )
      return false
    }
    true
  }
}
