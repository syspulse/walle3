package io.syspulse.wal3.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.wal3.{Wallet}
import io.syspulse.wal3.WalletSecret
import io.syspulse.wal3.signer.SignerSecret

class WalletStoreMany(wss:Seq[WalletStore] = Seq(new WalletStoreMem())) extends WalletStore {
  val log = Logger(s"${this}")

  val stores:Map[Option[String],WalletStore] = wss.map(ws => Some(ws.id) -> ws).toMap
  
  val DEFAULT_STORE="mem"

  def id:String = "many"

  def all(oid:Option[String]):Seq[WalletSecret] = all(Some(DEFAULT_STORE),oid)
  override def all(typ:Option[String],oid:Option[String]):Seq[WalletSecret] = 
    stores.get(typ) match {
      case Some(ws) => ws.all(oid)
      case None => 
        log.error(s"not found: ${typ}")
        Seq.empty
    }

  def size:Long = size(Some(DEFAULT_STORE))
  override def size(typ:Option[String]):Long = 
    stores.get(typ) match {
      case Some(ws) => ws.size
      case None => 
        log.error(s"not found: ${typ}")
        0L
    }

  def findByOid(oid:String):Seq[WalletSecret] = findByOid(Some(DEFAULT_STORE),oid)
  override def findByOid(typ:Option[String],oid:String):Seq[WalletSecret] = 
    stores.get(typ) match {
      case Some(ws) => ws.findByOid(oid)
      case None => 
        log.error(s"not found: ${typ}")
        Seq.empty
    }

  def +++(s:SignerSecret):Try[SignerSecret] = +++(Some(DEFAULT_STORE),s)
  override def +++(typ:Option[String],s:SignerSecret):Try[SignerSecret] =
    stores.get(typ) match {
      case Some(store) => store.+++(s)
      case None => 
        log.error(s"not found: ${typ}")
        Failure(new Exception(s"store not found: ${typ}"))
    }

  def +(w:WalletSecret):Try[WalletSecret] = this.+(Some(DEFAULT_STORE),w)

  override def +(typ:Option[String],w:WalletSecret):Try[WalletSecret] =
    stores.get(typ) match {
      case Some(store) => store.+(w)
      case None => 
        log.error(s"not found: ${typ}")
        Failure(new Exception(s"store not found: ${typ}"))
    }    

  def del(addr:String,oid:Option[String]):Try[WalletSecret] = del(Some(DEFAULT_STORE),addr,oid)
  override def del(typ:Option[String],addr:String,oid:Option[String]):Try[WalletSecret] =
    stores.get(typ) match {
      case Some(ws) => ws.del(addr,oid)
      case None => 
        log.error(s"not found: ${typ}")
        Failure(new Exception(s"store not found: ${typ}"))
    }

  def ???(addr:String,oid:Option[String]):Try[WalletSecret] = ???(Some(DEFAULT_STORE),addr,oid)
  override def ???(typ:Option[String],addr:String,oid:Option[String]):Try[WalletSecret] = 
    stores.get(typ) match {
      case Some(ws) => ws.???(addr,oid)
      case None => 
        log.error(s"not found: ${typ}")
        Failure(new Exception(s"store not found: ${typ}"))
    }
 
}
