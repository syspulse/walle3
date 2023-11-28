package io.syspulse.wal3.signer

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.wal3.WalletSecret

trait WalletSigner {
    
  def random(oid:Option[UUID]):Try[WalletSecret]
  def sign(sk:String,to:String,data:String):Try[String]
}

