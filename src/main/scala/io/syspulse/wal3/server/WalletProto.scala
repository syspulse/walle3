package io.syspulse.wal3.server

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.wal3.Wallet
import io.syspulse.blockchain.Blockchain
import spray.json.JsObject

final case class Wallets(wallets: Seq[Wallet],total:Option[Long]=None)

final case class WalletCreateReq(oid:Option[String],sk:String)
final case class WalletRandomReq(oid:Option[String])
final case class WalletRes(status:String,wallet: Option[Wallet])

final case class WalletSignReq(
  oid:Option[String],
  to:String,
  nonce:Long, 
  data:String,
  gasPrice:String,
  gasTip:String,
  gasLimit:Long,
  value:Option[String] = None,
  chain:Option[Blockchain] = Some(Blockchain.ANVIL),

  // signer specific data
  signerType: Option[String] = Some("eth1"),
  signerData: Option[JsObject] = None
)

final case class WalletTxReq(
  oid:Option[String],
  to:String,
  nonce:Long, 
  data:String,
  gasPrice:String,
  gasTip:String,
  gasLimit:Long,
  value:Option[String] = None,
  chain:Option[Blockchain] = Some(Blockchain.ANVIL)
)

final case class WalletCallReq(
  oid:Option[String],
  to:String,
  data:String,
  value:Option[String] = None,
  output:Option[String] = None,
  chain:Option[Blockchain] = Some(Blockchain.ANVIL)
)

final case class WalletBalanceReq(oid:Option[String],chains:Seq[Blockchain]=Seq())
final case class TxStatusReq(oid:Option[String],chain:Option[Blockchain])
final case class TxStatus(hash:String,status:String)

final case class TxCostReq(oid:Option[String],chain:Option[Blockchain],to:String,data:String)
final case class TxCost(
  cost:BigInt,
  price:BigInt,
  tip:Option[String]
)

final case class WalletSig(addr:String,sig:String)
final case class WalletTx(addr:String,txHash:String)

final case class BlockchainBalance(name:String,id:String,balance:BigInt,err:Option[String]=None)
final case class WalletBalance(addr:String,balances:Seq[BlockchainBalance])

final case class BlockchainReq(chain:Option[Blockchain])
final case class GasPrice(gas:BigInt,tok:Option[String]=None,dec:Option[Int]=None)

final case class WalletCall(addr:String,result:String)