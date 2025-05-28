package net.bdew.ae2stuff.misc

object WirelessKitModes extends Enumeration {
  case class QUEUING() extends super.Val
  case class BINDING() extends super.Val

  type WirelessKitModes = Value
  val MODE_QUEUING: QUEUING = QUEUING()
  val MODE_QUEUING_LINE: QUEUING = QUEUING()

  val MODE_BINDING: BINDING = BINDING()
  val MODE_BINDING_LINE: BINDING = BINDING()
}
