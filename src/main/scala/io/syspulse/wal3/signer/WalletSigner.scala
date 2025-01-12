package io.syspulse.wal3.signer

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.wal3.WalletSecret

abstract class SignerPayload

case class SignerTxPayload(
  to:String,
  nonce:Long,
  data:String,
  gasPrice:BigInt,
  gasTip:BigInt,
  gasLimit:Long,
  value:BigInt,
  chainId:Long
) extends SignerPayload

abstract class SignerData

case class SignerSecret(ws:WalletSecret,data:Option[SignerData]=None)

trait WalletSigner {

  def random(oid:Option[String]):Try[WalletSecret]
  def create(oid:Option[String],sk:String):Try[WalletSecret]
  def sign(ss:SignerSecret, payload:SignerPayload):Try[String]
}

