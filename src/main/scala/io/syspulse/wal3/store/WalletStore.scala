package io.syspulse.wal3.store

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.wal3.{Wallet,WalletSecret}
import io.syspulse.wal3.signer.WalletSigner
import io.syspulse.wal3.signer.SignerSecret

trait WalletStore extends Store[WalletSecret,String] {  
  
  def id:String

  def getKey(w: WalletSecret): String = w.addr
  
  def +(typ:Option[String],w:WalletSecret):Try[WalletSecret] = this.+(w)

  def +++(typ:Option[String],s:SignerSecret):Try[SignerSecret] = this.+++(s)
  def +++(s:SignerSecret):Try[SignerSecret]
  
  def del(typ:Option[String],addr:String,oid:Option[String]):Try[WalletSecret] = this.del(addr,oid)
  def del(addr:String,oid:Option[String]):Try[WalletSecret]

  def ???(typ:Option[String],addr:String,oid:Option[String]):Try[WalletSecret] = this.???(addr,oid)
  def ???(addr:String,oid:Option[String]):Try[WalletSecret]
  
  def all(typ:Option[String],oid:Option[String]):Seq[WalletSecret] = this.all(oid)
  def all(oid:Option[String]):Seq[WalletSecret]
  
  def size(typ:Option[String]):Long = this.size
  def size:Long

  def findByOid(typ:Option[String],oid:String):Seq[WalletSecret] = this.findByOid(oid)
  def findByOid(oid:String):Seq[WalletSecret]

  def ?(addr:String,typ:Option[String]):Try[WalletSecret] = this.?(addr)
  def ?(addr:String):Try[WalletSecret] = ???(addr,None)
  // def +(w:WalletSecret):Try[WalletStore] = { 
  //   this.+++(w).map(_ => this)
  // }

  def all:Seq[WalletSecret] = all(None)
  
  def del(typ:Option[String],addr:String):Try[String] = del(typ,addr,None).map(_ => addr)
  def del(addr:String):Try[String] = del(addr,None).map(_ => addr)
}

