package net.bdew.ae2stuff.machines.wireless

import net.bdew.lib.data.base.{DataSlotContainer, DataSlotVal, UpdateKind}
import net.bdew.lib.Misc
import net.bdew.lib.block.BlockRef
import net.minecraft.nbt.{NBTTagCompound, NBTTagList}

case class WirelessDataSlot(name: String, parent: DataSlotContainer)
    extends DataSlotVal[Set[BlockRef]] {

  override var value: Set[BlockRef] = Set[BlockRef]()

  setUpdate(UpdateKind.SAVE, UpdateKind.WORLD)

  def save(tag: NBTTagCompound, kind: UpdateKind.Value): Unit = {
    val tagList = new NBTTagList
    value foreach (blockRef =>
      tagList.appendTag(
        Misc.applyMutator(blockRef.writeToNBT, new NBTTagCompound)
      )
    )
    tag.setTag(name, tagList)
  }

  def load(tag: NBTTagCompound, kind: UpdateKind.Value): Unit = {
    value = tag.getTag(name) match {
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
  }
}
