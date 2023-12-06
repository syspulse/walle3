package io.syspulse.wal3.server

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.wal3.Wallet

final case class Wallets(wallets: Seq[Wallet],total:Option[Long]=None)

final case class WalletCreateReq(oid:Option[UUID],sk:String)

final case class WalletRandomReq(oid:Option[UUID])
final case class WalletRes(status:String,wallet: Option[Wallet])

final case class WalletSignReq(oid:Option[UUID],to:String,
  nonce:Long, data:String,
  gasPrice:String,gasTip:String,gasLimit:Long,
  value:Option[String] = None,chainId:Option[Long] = Some(11155111)
)

final case class WalletTxReq(oid:Option[UUID],to:String,
  nonce:Long, data:String,
  gasPrice:String,gasTip:String,gasLimit:Long,
  value:Option[String] = None,chainId:Option[Long] = Some(11155111)
)

final case class WalletBalanceReq(oid:Option[UUID],blockchains:Seq[String]=Seq())

final case class WalletSig(addr:String,sig:String)
final case class WalletTx(addr:String,txHash:String)

final case class BlockchainBalance(name:String,id:Long,balance:BigInt)
final case class WalletBalance(addr:String,balances:Seq[BlockchainBalance])
