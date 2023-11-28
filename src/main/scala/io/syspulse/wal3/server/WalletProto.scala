package io.syspulse.wal3.server

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.wal3.Wallet

final case class Wallets(wallets: Seq[Wallet],total:Option[Long]=None)

final case class WalletCreateReq(oid:Option[UUID],mnemo:Option[String] = None)
final case class WalletRandomReq()
final case class WalletRes(status:String,wallet: Option[Wallet])

final case class WalletSignReq(oid:Option[UUID],to:String,data:String)
final case class WalletSignature(addr:String,sig:String)