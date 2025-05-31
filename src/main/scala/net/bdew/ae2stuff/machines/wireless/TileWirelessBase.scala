package net.bdew.ae2stuff.machines.wireless

import appeng.api.implementations.tiles.IColorableTile
import appeng.api.networking.{IGridConnection, IGridNode}
import appeng.api.networking.security.IActionHost
import appeng.api.util.AEColor
import appeng.helpers.ICustomNameObject
import net.bdew.ae2stuff.grid.{GridTile, VariableIdlePower}
import net.bdew.ae2stuff.machines.wireless.simple.BlockWireless
import net.bdew.lib.block.BlockRef
import net.bdew.lib.data.base.{DataSlotVal, TileDataSlots, UpdateKind}
import net.minecraft.block.Block
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

import java.util
import scala.collection.mutable
import scala.collection.immutable

abstract class TileWirelessBase
    extends TileDataSlots
    with GridTile
    with IActionHost
    with VariableIdlePower
    with ICustomNameObject
    with IColorableTile {

  protected val cfg: MachineWireless.type = MachineWireless

  var customName: String = ""

  var color: AEColor = AEColor.Transparent

  lazy val myPos: BlockRef = BlockRef.fromTile(this)

  def canAddLink: Boolean

  val maxConnections: Int


  private val connectedTarget: WirelessDataSlot = WirelessDataSlot("connectedTarget", this)
  //private val connectedTarget = List.empty[TileWirelessBase]
  private val connections = List.empty[IGridConnection]

  def getConnectedTiles: immutable.List[TileWirelessBase] = connectedTarget.filter(_.getTile[TileWirelessBase](worldObj).nonEmpty).map(_.get)
  def getAllConnection: immutable.List[IGridConnection] = connections

  def isConnectedTo(other: TileWirelessBase): Boolean =
    connectedTarget.contains(other) && other.connectedTarget.contains(this)

  def isLinked: Boolean = connectedTarget.nonEmpty

  def isHub: Boolean = maxConnections > 1

  def getAvailableConnections: Int

  def getUsedChannels: Int = getAllConnection.map(_.getUsedChannels).sum

  def doLink(other: TileWirelessBase): Boolean

  def doUnlink(other: TileWirelessBase): Unit = doUnlink()

  def doUnlink(): Unit

  override def shouldRefresh(
      oldBlock: Block,
      newBlock: Block,
      oldMeta: Int,
      newMeta: Int,
      world: World,
      x: Int,
      y: Int,
      z: Int
  ): Boolean =
    newBlock != BlockWireless

  override def doSave(kind: UpdateKind.Value, t: NBTTagCompound): Unit = {
    super.doSave(kind, t)
    if (customName != null) {
      t.setString("CustomName", customName)
    }
    t.setShort("Color", color.ordinal().toShort)
  }

  override def doLoad(kind: UpdateKind.Value, t: NBTTagCompound): Unit = {
    super.doLoad(kind, t)
    if (t.hasKey("CustomName")) {
      this.customName = t.getString("CustomName")
    }
    if (!t.hasKey("Color")) {
      t.setShort("Color", AEColor.Transparent.ordinal().toShort)
    }
    val colorIdx = t.getShort("Color").toInt
    this.color = AEColor.values().apply(colorIdx)
    if (hasWorldObj) {
      worldObj.markBlockRangeForRenderUpdate(
        xCoord,
        yCoord,
        zCoord,
        xCoord,
        yCoord,
        zCoord
      )
    }
  }

  override def getMachineRepresentation: ItemStack = new ItemStack(
    BlockWireless
  )

  override def recolourBlock(
      side: ForgeDirection,
      colour: AEColor,
      who: EntityPlayer
  ): Boolean = {
    if (this.color == colour) {
      return false
    }

    this.color = colour
    if (getGridNode(side) != null) {
      getGridNode(side).updateState()
      worldObj.markBlockForUpdate(xCoord, yCoord, zCoord)
      markDirty()
    }
    true
  }

  override def getColor: AEColor = color

  override def getGridColor: AEColor = color

  override def getCustomName: String = customName

  override def hasCustomName: Boolean = customName != null

  override def setCustomName(name: String): Unit = {
    this.customName = name
    getConnectedTiles.foreach(te => te.customName = name)
    markDirty()
  }

  override def getActionableNode: IGridNode = this.node

  def getDrops(
      world: World,
      x: Int,
      y: Int,
      z: Int,
      metadata: Int,
      fortune: Int,
      drops: util.ArrayList[ItemStack]
  ): util.ArrayList[ItemStack]
}
