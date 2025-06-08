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
import io.syspulse.wal3.signer.SignerSecret
import io.syspulse.wal3.signer.SignerSSSUserShare

case class MasterShares(shares:List[String],seed:List[String])

// sss://k   - how many master shares. Default is 1. Store master in WalletSecret
// sss://k/mem

class SSSUri(uri:String) {
  val PREFIX="sss://"

  val (k,store) = uri.stripPrefix(PREFIX).split(":").toList match {
    case k :: Nil => (k.toInt, "key")
    case k :: store :: Nil => (k.toInt, store)
    case _ => (1, "key")
  }
}

// number of master shares
class WalletStoreSSS(uri:String) extends WalletStoreMem {

  val sssConfig = new SSSUri(uri)

  override def id:String = "sss"

  type Address = String

  // master shares
  var masters:Map[Address,MasterShares] = Map()

  override def +++(s:SignerSecret):Try[SignerSecret] = {
    val w = s.ws
    
    // extract master shares 
    val shares = SecretShare.fromList(w.sk)
    val seeds = SecretShare.fromList(w.metadata)
    val masterShares = shares.take(sssConfig.k)
    val masterSeed = seeds.take(sssConfig.k)
    val userShares = shares.drop(sssConfig.k)
    val userSeed = seeds.drop(sssConfig.k)
    
    // new wallet with only masters shares
    val w1 = w.copy(sk = SecretShare.toList(masterShares),metadata = SecretShare.toList(masterSeed))
    val s1 = s.copy(ws = w1)

    super.+++(s1) match {
      case Success(s2) =>
        // save master shares
        log.debug(s"${w.addr}: Masters: ${masterShares}, User: ${userShares}")
        
        // masters = masters + (w1.addr -> MasterShares(masterShares,masterSeed))
        
        // return modified wallet without first k shares
        val w2 = w.copy(sk = SecretShare.toList(userShares),metadata = SecretShare.toList(userSeed))
        val s2 = s.copy(ws = w2, data = Some(SignerSSSUserShare(userShares)))
        Success(s2)

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
