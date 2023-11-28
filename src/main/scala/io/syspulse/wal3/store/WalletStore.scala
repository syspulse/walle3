package io.syspulse.wal3.store

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.wal3.WalletSecret

trait WalletStore extends Store[WalletSecret,String] {
  
  def getKey(w: WalletSecret): String = w.addr
  
  def +++(w:WalletSecret):Try[WalletSecret]
  
  def del(addr:String,oid:Option[UUID]):Try[WalletSecret]
  
  def ???(addr:String,oid:Option[UUID]):Try[WalletSecret]  

  def all(oid:Option[UUID]):Seq[WalletSecret]
  
  def size:Long
}

