package io.syspulse.wal3.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import os._
import io.jvm.uuid._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.store.StoreDir

import io.syspulse.wal3.WalletSecret
import io.syspulse.wal3.server.WalletJson._

// Preload from file during start
class WalletStoreDir(dir:String = "store/") extends StoreDir[WalletSecret,String](dir) with WalletStore {
  val store = new WalletStoreMem()

  def id:String = "dir"

  def toKey(addr:String):String = addr

  def all(oid:Option[String]):Seq[WalletSecret] = store.all(oid)
  
  def size:Long = store.size
  
  override def +++(w:WalletSecret):Try[WalletSecret] = for {
    _ <- store.+(w)
    _ <- super.+(w)
  } yield w

  override def +(w:WalletSecret):Try[WalletSecret] = store.+(w).map(_ => w)

  override def del(addr:String,oid:Option[String]):Try[WalletSecret] = for {
    w <- store.del(addr,oid)
    _ <- super.del(addr)
  } yield w

  override def del(addr:String):Try[String] = this.del(addr).map(_ => addr)    
  
  def ???(addr:String,oid:Option[String]):Try[WalletSecret] = store.???(addr,oid)

  override def findByOid(oid:String):Seq[WalletSecret] = store.findByOid(oid)
    
  // preload and watch
  load(dir)
  watch(dir)
}