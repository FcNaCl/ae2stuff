package net.bdew.ae2stuff.machines.wireless

import appeng.api.AEApi
import appeng.api.config.PowerMultiplier
import appeng.api.implementations.tiles.IColorableTile
import appeng.api.networking.{GridFlags, IGridConnection, IGridNode}
import appeng.api.networking.security.IActionHost
import appeng.api.util.AEColor
import appeng.helpers.ICustomNameObject
import net.bdew.ae2stuff.AE2Stuff
import net.bdew.ae2stuff.grid.{GridTile, VariableIdlePower}
import net.bdew.ae2stuff.machines.wireless.hub.{BlockWirelessHub, TileWirelessHub}
import net.bdew.ae2stuff.machines.wireless.simple.{BlockWireless, TileWireless}
import net.bdew.lib.block.BlockRef
import net.bdew.lib.data.base.{DataSlotVal, TileDataSlots, UpdateKind}
import net.bdew.lib.multiblock.data.DataSlotPos
import net.minecraft.block.Block
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

import java.util
import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.mutable
import scala.collection.immutable
import scala.util.Failure

abstract class TileWirelessBase()
    extends TileDataSlots
    with GridTile
    with IActionHost
    with VariableIdlePower
    with ICustomNameObject
    with IColorableTile {

  serverTick.listen(() => {
    getConnectedTiles.foreach(other => {
      connections.get(other) match {
        case Some(connection) =>
        case None if (other.canAddLink) =>
          try {
            doLink(other)
          } catch {
            case t: Throwable =>
              AE2Stuff.logWarnException(
                "Failed setting up wireless link %s <-> %s: %s",
                t,
                myPos,
                other,
                t.getMessage
              )
              doUnlink()
          }
        case None =>
      }
    })
  })

  val link =
    DataSlotPos("link", this).setUpdate(UpdateKind.SAVE, UpdateKind.WORLD)


  protected val cfg: MachineWireless[_ <: Block] = MachinesWirelessRegister
    .get(this)
    .getOrElse(
      throw new IllegalStateException(
        s"Unknown wireless block type for ${getClass.getName} wireless tile"
      )
    )

  var customName: String = ""

  var color: AEColor = AEColor.Transparent

  lazy val myPos: BlockRef = BlockRef.fromTile(this)

  val block: BlockWirelessBase[TileWirelessBase] = this match {
    case _: TileWireless =>
      BlockWireless.asInstanceOf[BlockWirelessBase[TileWirelessBase]]
    case _: TileWirelessHub =>
      BlockWirelessHub.asInstanceOf[BlockWirelessBase[TileWirelessBase]]
    case _ =>
      AE2Stuff.logWarn(
        "TileWirelessBase %s is not a BlockWirelessBase, this is a bug",
        this
      )
      throw new IllegalStateException(
        "un supported TileWirelessBase type: " + this.getClass.getName
      )
  }

  override def getFlags: util.EnumSet[GridFlags] =
    util.EnumSet.of(GridFlags.DENSE_CAPACITY)

  val maxConnections: Int

  private val connectedTargets: WirelessDataSlot =
    WirelessDataSlot("connectedTarget", this).setUpdate(UpdateKind.SAVE, UpdateKind.WORLD)

  // server-side only, contains the connections to other wireless tiles
  private var connections =
    mutable.HashMap.empty[TileWirelessBase, IGridConnection]

  def getConnectedTiles: Set[TileWirelessBase] =
    connectedTargets.flatMap(_.getTile[TileWirelessBase](worldObj))

  def getAllConnections: Set[IGridConnection] = connections.values.toSet

  def getTileAndConnections
      : immutable.HashMap[TileWirelessBase, IGridConnection] =
    immutable.HashMap(connections.toSeq: _*)

  def isConnectedTo(other: TileWirelessBase): Boolean =
    connectedTargets.exists(_ == other.myPos) && other.connectedTargets.exists(
      _ == myPos
    )

  def isLinked: Boolean = connectedTargets.nonEmpty

  def isHub: Boolean = maxConnections > 1

  def getAvailableConnections: Int =
    maxConnections - connections.size

  def canAddLink: Boolean = getAvailableConnections > 0

  def getUsedChannels: Int = {
    var channels = 0
    getGridNode(ForgeDirection.UNKNOWN).getConnections.asScala.foreach {
      connection =>
        channels = math.max(
          channels,
          connection.getUsedChannels
        )
    }
    channels
  }

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
    block
  )

  protected def setupConnection(other: TileWirelessBase): Boolean = {
    val connection = Option(
      AEApi.instance().createGridConnection(this.getNode, other.getNode)
    ).getOrElse(return false)

    connectedTargets.value = connectedTargets.value + other.myPos
    other.connectedTargets.value = other.connectedTargets.value + myPos

    connections.put(other, connection)
    other.connections.put(this, connection)

    this.computeEnergyUsage()

    other.computeEnergyUsage()

    if (worldObj.blockExists(xCoord, yCoord, zCoord)) {
      worldObj.setBlockMetadataWithNotify(
        this.xCoord,
        this.yCoord,
        this.zCoord,
        1,
        3
      )
    }
    if (worldObj.blockExists(other.xCoord, other.yCoord, other.zCoord)) {
      worldObj.setBlockMetadataWithNotify(
        other.xCoord,
        other.yCoord,
        other.zCoord,
        1,
        3
      )
    }

    dataSlotChanged(connectedTargets)
    other.dataSlotChanged(other.connectedTargets)
    true
  }

  protected def breakConnection(other: TileWirelessBase): Unit = {
    connections.get(other) match {
      case Some(connection) =>
        connection.destroy()
      case None =>
        return
    }

    connectedTargets.value = connectedTargets.value - other.myPos
    other.connectedTargets.value = other.connectedTargets.value - myPos

    connections.remove(other)
    other.connections.remove(this)

    other.computeEnergyUsage()
    if (worldObj.blockExists(other.xCoord, other.yCoord, other.zCoord)) {
      worldObj.setBlockMetadataWithNotify(
        other.xCoord,
        other.yCoord,
        other.zCoord,
        0,
        3
      )
      connections.remove(other)
      other.connections.remove(other)
    }
    this.computeEnergyUsage()
    if (worldObj.blockExists(xCoord, yCoord, zCoord))
      worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, 0, 3)
  }

  protected def breakAllConnection(): Unit = {
    getConnectedTiles.foreach(breakConnection(_))
  }

  def computeEnergyUsage(): Unit = {
    this.setIdlePowerUse(
      PowerMultiplier.CONFIG.multiply( // apply config multiplier of AE2
        getConnectedTiles
          .map(tile => {
            val dx = this.xCoord - tile.xCoord
            val dy = this.yCoord - tile.yCoord
            val dz = this.zCoord - tile.zCoord
            // val power = cfg.powerBase + cfg.powerDistanceMultiplier * (dx * dx + dy * dy + dz * dz)
            val dist = math.sqrt(dx * dx + dy * dy + dz * dz)
            cfg.powerBase + cfg.powerDistanceMultiplier * dist * math.log(
              dist * dist + 3
            )
          })
          .sum
      )
    )
  }

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
