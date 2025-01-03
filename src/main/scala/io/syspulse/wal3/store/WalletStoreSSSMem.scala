package io.syspulse.wal3.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.wal3.{Wallet}
import io.syspulse.wal3.WalletSecret
import org.secret_sharing.Share
import io.syspulse.wal3.signer.SecretShare

case class MasterShares(shares:List[String],seed:List[String])

// number of master shares
class WalletStoreSSSMem(m:Int = 1) extends WalletStoreMem {

  override def id:String = "sss-mem"

  type Address = String

  // master shares
  var masters:Map[Address,MasterShares] = Map()

  override def +++(w:WalletSecret):Try[WalletSecret] = {
    // extract master shares and save them here
    val shares = SecretShare.fromList(w.sk)
    val seeds = SecretShare.fromList(w.metadata)
    val masterShares = shares.take(m)
    val masterSeed = seeds.take(m)
    val userShares = shares.drop(m)
    val userSeed = seeds.drop(m)
    
    // new wallet with only user shares
    val w1 = w.copy(sk = SecretShare.toList(userShares),metadata = SecretShare.toList(userSeed))

    super.+++(w1) match {
      case Success(w) =>
        // save master shares
        log.debug(s"${w1.addr}: Masters: ${masterShares}, User: ${userShares}")
        masters = masters + (w1.addr -> MasterShares(masterShares,masterSeed))
        // return modified wallet
        Success(w1)

      case f => f
    }
  }

  override def del(addr:String,oid:Option[String]):Try[WalletSecret] = {
    super.del(addr,oid) match {
      case Success(w) =>
        masters = masters - w.addr
        Success(w)
      case f => f
    }
  }
   
}
