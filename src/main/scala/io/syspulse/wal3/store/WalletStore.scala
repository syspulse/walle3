package io.syspulse.wal3.store

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.wal3.{Wallet,WalletSecret}
import io.syspulse.wal3.signer.WalletSigner

trait WalletStore extends Store[WalletSecret,String] {  
  
  def getKey(w: WalletSecret): String = w.addr
  
  def +++(w:WalletSecret):Try[WalletSecret]
  
  def del(addr:String,oid:Option[String]):Try[WalletSecret]
  
  def ???(addr:String,oid:Option[String]):Try[WalletSecret]
  
  def all(oid:Option[String]):Seq[WalletSecret]
  
  def size:Long

  def findByOid(oid:String):Seq[WalletSecret]

  def ?(addr:String):Try[WalletSecret] = ???(addr,None)
  // def +(w:WalletSecret):Try[WalletStore] = { 
  //   this.+++(w).map(_ => this)
  // }
  def all:Seq[WalletSecret] = all(None)
  def del(addr:String):Try[String] = del(addr,None).map(_ => addr)  
}

