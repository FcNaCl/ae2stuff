/*
 * Copyright (c) bdew, 2014 - 2015
 * https://github.com/bdew/ae2stuff
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package net.bdew.ae2stuff.waila

import appeng.api.config.PowerMultiplier
import appeng.api.util.AEColor
import mcp.mobius.waila.api.{IWailaConfigHandler, IWailaDataAccessor}
import net.bdew.ae2stuff.machines.wireless.TileWirelessBase
import net.bdew.lib.block.BlockRef
import net.bdew.lib.nbt.NBT
import net.bdew.lib.{DecFormat, Misc}
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{NBTTagCompound, NBTTagList}
import net.minecraft.world.World

import scala.collection.mutable.ListBuffer

object WailaWirelessDataProvider
    extends BaseDataProvider(classOf[TileWirelessBase]) {
  override def getNBTTag(
      player: EntityPlayerMP,
      te: TileWirelessBase,
      tag: NBTTagCompound,
      world: World,
      x: Int,
      y: Int,
      z: Int
  ): NBTTagCompound = {
    val tagList = new NBTTagList
    te.getConnectedTiles foreach (blockRef =>
      tagList.appendTag(
        Misc.applyMutator(blockRef.myPos.writeToNBT, new NBTTagCompound)
      )
    )

    val data = NBT(
      "connected" -> te.isLinked,
      "targets" -> tagList,
      "channels" -> te.getUsedChannels,
      "power" -> te.getIdlePowerUsage,
      "color" -> te.color.ordinal(),
      "isSneaking" -> player.isSneaking
    )

    if (te.hasCustomName) {
      data.setString("name", te.customName)
    }
    tag.setTag("wireless_waila", data)
    tag
  }

  override def getBodyStrings(
      target: TileWirelessBase,
      stack: ItemStack,
      acc: IWailaDataAccessor,
      cfg: IWailaConfigHandler
  ): Iterable[String] = {
    if (!acc.getNBTData.hasKey("wireless_waila")) return List.empty[String]

    val data = acc.getNBTData.getCompoundTag("wireless_waila")
    val name = Option(data.getString("name")).getOrElse("")
    val color = data.getInteger("color")

    val pos = data.getTag("targets") match {
      case tagList: NBTTagList =>
        (for (i <- 0 until tagList.tagCount()) yield {
          tagList.getCompoundTagAt(i) match {
            case a: NBTTagCompound =>
              BlockRef.fromNBT(a)
            case _ =>
              null
          }
        }).filterNot(_ == null).toSet
      case _ => Set.empty[BlockRef]
    }

    val lines = ListBuffer.empty[String]

    if (data.getBoolean("isSneaking")) {
      lines += Misc.toLocalF("ae2stuff.waila.wireless.connected.details.title")

      pos.foreach(singlePos =>
        lines += Misc.toLocalF(
          "ae2stuff.waila.wireless.connected.details",
          singlePos.x,
          singlePos.y,
          singlePos.z
        )
      )
      return lines.toList
    }

    lines += (pos.size match {
      case 0 => Misc.toLocal("ae2stuff.waila.wireless.notconnected")
      case 1 =>
        val singlePos = pos.head
        Misc.toLocalF(
          "ae2stuff.waila.wireless.connected",
          singlePos.x,
          singlePos.y,
          singlePos.z
        )
      case _ =>
        Misc.toLocalF(
          "ae2stuff.waila.wireless.connected.multiple",
          pos.size
        )

    })

    if (data.getBoolean("connected")) {
      lines +=
        Misc.toLocalF(
          "ae2stuff.waila.wireless.channels",
          data.getInteger("channels")
        )
    }

    lines += Misc.toLocalF(
      "ae2stuff.waila.wireless.power",
      DecFormat.short(data.getDouble("power"))
    )

    if (color != AEColor.Transparent.ordinal()) {
      lines += Misc.toLocal(AEColor.values().apply(color).unlocalizedName)
    }

    lines.toList
  }
}
