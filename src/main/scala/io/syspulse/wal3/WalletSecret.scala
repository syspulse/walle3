package io.syspulse.wal3

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.wal3.cypher.Cypher
import io.syspulse.skel.util.Util

case class WalletSecret(  
  sk:String,  
  pk:String,
  addr:String,

  oid:Option[String],
  
  typ:String = "ECDSA",
  ts:Long = System.currentTimeMillis(),

  cypher:String = "",
  metadata:String = ""          // metadata for different signers/cyphers (e.g. used by KMS for datakey or AES seed)
) {
  override def toString = s"WalletSecret(${sk.take(4)}*****,${pk},${addr},${oid},${typ},${ts},${cypher},${Util.hex(metadata.getBytes())})"
}
