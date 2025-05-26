/*
 * Copyright (c) bdew, 2014 - 2015
 * https://github.com/bdew/ae2stuff
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package net.bdew.ae2stuff.misc

import net.bdew.ae2stuff.machines.wireless.TileWireless
import net.bdew.lib.block.BlockRef
import net.bdew.lib.nbt.NBT
import net.minecraft.item.{Item, ItemStack}
import net.minecraft.nbt.{NBTBase, NBTTagCompound, NBTTagList}
import net.minecraft.world.World

trait AdvItemLocationStore extends Item {

  private val COMPOUND_TAG = NBTBase.NBTTypes.indexOf("COMPOUND")

  def addLocation(
      stack: ItemStack,
      loc: BlockRef,
      dimension: Int,
      isHub: Boolean
  ): Boolean = {
    if (!stack.hasTagCompound) stack.setTagCompound(new NBTTagCompound)
    val tag = stack.getTagCompound
    if (tag.hasKey("dim") && tag.getInteger("dim") != dimension) {
      false
    }
    val locList = tag.getTagList("loc", COMPOUND_TAG)
    for (i <- 0 until locList.tagCount()) {
      val tag = locList.getCompoundTagAt(i)
      val pos = BlockRef.fromNBT(tag)
      if (pos == loc && !isHub) {
        return false
      }
    }
    locList.appendTag(NBT.from(loc.writeToNBT _))
    tag.setTag("loc", locList)
    tag.setInteger("dim", dimension)
    true
  }

  def getLocations(stack: ItemStack) = {
    if (!stack.hasTagCompound) stack.setTagCompound(new NBTTagCompound)
    val tag = stack.getTagCompound
    if (tag.hasKey("loc")) {
      val locList = tag.getTagList("loc", COMPOUND_TAG)
      locList
    } else {
      tag.setTag("loc", new NBTTagCompound)
      tag.getTagList("loc", COMPOUND_TAG)
    }
  }

  // location is going to be a queue of coordinates

  def hasLocation(stack: ItemStack): Boolean = {
    if (
      stack.getItem == this && stack.hasTagCompound && stack.getTagCompound
        .hasKey("loc")
    ) {
      // check if list is not empty
      val loc = stack.getTagCompound.getTagList("loc", COMPOUND_TAG)
      if (loc.tagCount() > 0) {
        return true
      }
    }
    false
  }

  def getNextLocation(stack: ItemStack): BlockRef =
    BlockRef.fromNBT(
      stack.getTagCompound.getTagList("loc", COMPOUND_TAG).getCompoundTagAt(0)
    )

  private[misc] class TileWirelessIterator(
      stack: ItemStack,
      world: World,
      target: TileWireless
  ) {
    private val tags: NBTTagList =
      stack.getTagCompound.getTagList("loc", COMPOUND_TAG)
    private var wireless: Option[TileWireless] = None
    private var canNext: Boolean = false

    def hasNext: Boolean = {
      if (canNext) {
        return true
      }

      while (wireless.isEmpty && tags.tagCount() > 0) {
        wireless = BlockRef
          .fromNBT(tags.removeTag(0).asInstanceOf[NBTTagCompound])
          .getTile[TileWireless](world)
        wireless match {
          case Some(w) if w != target =>
            canNext = true
            return true
        }
      }
      false
    }

    def next(): TileWireless = {
      if (!canNext && !hasNext) {
        throw new NoSuchElementException("No more locations available")
      }
      canNext = false
      wireless.get
    }
    def foreach(f: TileWireless => Unit): Unit = { while (hasNext) f(next()) }
  }

  def iterOnValidLocation(
      stack: ItemStack,
      world: World,
      target: TileWireless
  ): TileWirelessIterator = {
    new TileWirelessIterator(stack, world, target)
  }

  def getDimension(stack: ItemStack): Int =
    stack.getTagCompound.getInteger("dim")

  def setLocation(stack: ItemStack, loc: BlockRef, dimension: Int): Unit = {
    if (!stack.hasTagCompound) stack.setTagCompound(new NBTTagCompound)
    val tag = stack.getTagCompound
    val locList = tag.getTagList("loc", COMPOUND_TAG)
    locList.appendTag(NBT.from(loc.writeToNBT _))
    tag.setTag("loc", locList)
    tag.setInteger("dim", dimension)
  }

  def popLocation(stack: ItemStack): BlockRef = {
    if (stack.hasTagCompound) {
      val locList = stack.getTagCompound.getTagList("loc", COMPOUND_TAG)
      if (locList.tagCount() > 0) {
        val tag = locList.getCompoundTagAt(0)
        locList.removeTag(0)
        val pos = BlockRef.fromNBT(tag)
        stack.getTagCompound.setTag("loc", locList)
        return pos;
      }
      if (locList.tagCount() == 0) {
        stack.getTagCompound.removeTag("loc")
        stack.getTagCompound.removeTag("dim")
      }
    }
    null
  }

  /** Retrieves the mode state of the given ItemStack. If the mode is not
    * previously set, initializes it to a default value of `false`.
    *
    * @param stack
    *   the ItemStack from which to retrieve or initialize the mode state
    * @return
    *   the current mode state of the ItemStack as a Boolean
    */
  def getMode(stack: ItemStack): Boolean = {
    if (!stack.hasTagCompound) stack.setTagCompound(new NBTTagCompound)
    val tag = stack.getTagCompound
    if (tag.hasKey("mode_binding")) {
      return tag.getBoolean("mode_binding")
    }
    tag.setBoolean("mode_binding", false)
    false
  }

  def toggleMode(stack: ItemStack): Boolean = {
    if (!stack.hasTagCompound) stack.setTagCompound(new NBTTagCompound)
    val tag = stack.getTagCompound
    if (tag.hasKey("mode_binding")) {
      tag.setBoolean("mode_binding", !tag.getBoolean("mode_binding"))
    } else {
      tag.setBoolean("mode_binding", false)
    }
    tag.getBoolean("mode_binding")
  }
  def toggleLineMode(stack: ItemStack): Boolean = {
    if (!stack.hasTagCompound) stack.setTagCompound(new NBTTagCompound)
    val tag = stack.getTagCompound
    if (tag.hasKey("lineMode")) {
      tag.setBoolean("lineMode", !tag.getBoolean("lineMode"))
    } else {
      tag.setBoolean("lineMode", false)
    }
    tag.getBoolean("lineMode")
  }
};
