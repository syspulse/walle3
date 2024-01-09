package io.syspulse.wal3.signer

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.wal3.WalletSecret

trait WalletSigner {
    
  def random(oid:Option[String]):Try[WalletSecret]
  def create(oid:Option[String],sk:String):Try[WalletSecret]
  def sign(ws:WalletSecret,to:String,nonce:Long,data:String,
           gasPrice:BigInt,gasTip:BigInt,gasLimit:Long,
           value:BigInt,chainId:Long):Try[String]
}

