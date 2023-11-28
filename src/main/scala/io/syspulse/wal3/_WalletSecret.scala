package io.syspulse.wal3

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

case class _WalletSecret(  
  sk:String,
  pk:String,
  addr:String,

  oid:Option[UUID],
  
  typ:String = "ECDSA",
  ts:Long = System.currentTimeMillis()
)

