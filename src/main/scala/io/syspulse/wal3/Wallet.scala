package io.syspulse.wal3

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

case class Wallet(  
  addr:String,
  typ:String,
  ts:Long
)
