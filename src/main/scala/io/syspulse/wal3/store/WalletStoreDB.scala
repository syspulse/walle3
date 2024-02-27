package io.syspulse.wal3.store

import scala.util.Try
import scala.util.{Success,Failure}

import io.jvm.uuid._

import io.getquill._
import io.getquill.context._

import scala.jdk.CollectionConverters._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.config.{Configuration}
import io.syspulse.skel.store.{Store,StoreDB}

import io.syspulse.wal3.WalletSecret

// Postgres does not support table name 'wal3' !
class WalletStoreDB(configuration:Configuration,dbConfigRef:String) 
  extends StoreDB[WalletSecret,String](dbConfigRef,"wallet_secret",Some(configuration)) 
  with WalletStore {

  import ctx._
  
  val table = dynamicQuerySchema[WalletSecret](tableName)
  
  def indexOid = "wallet_oid"

  // ATTENTION: called from constructor, so derived class vals are not initialized yet !
  def create:Try[Long] = {    
    val CREATE_INDEX_MYSQL_SQL = s"CREATE INDEX ${indexOid} ON ${tableName} (oid);"
    val CREATE_INDEX_POSTGRES_SQL = s"CREATE INDEX IF NOT EXISTS ${indexOid} ON ${tableName} (oid);"
    
    val CREATE_INDEX_SQL = getDbType match {
      case "mysql" => CREATE_INDEX_MYSQL_SQL
      case "postgres" => CREATE_INDEX_POSTGRES_SQL
    }

    // Base64 encoded encrypted key (SK) can be quite long on KMS
    val CREATE_TABLE_MYSQL_SQL = 
      s"""CREATE TABLE IF NOT EXISTS ${tableName} (
        addr VARCHAR(42) PRIMARY KEY, 
        sk VARCHAR(2048),
        pk VARCHAR(130),
        oid VARCHAR(36), 
        typ VARCHAR(64),        
        ts BIGINT,
        cypher VARCHAR(64),
        metadata VARCHAR(256)
      );
      """

    val CREATE_TABLE_POSTGRES_SQL = 
      s"""CREATE TABLE IF NOT EXISTS ${tableName} (
        addr VARCHAR(42) PRIMARY KEY, 
        sk VARCHAR(2048),
        pk VARCHAR(130),
        oid VARCHAR(36),
        typ VARCHAR(64),        
        ts BIGINT,
        cypher VARCHAR(64),
        metadata VARCHAR(256)
      );
      """

    val CREATE_TABLE_SQL = getDbType match {
      case "mysql" => CREATE_TABLE_MYSQL_SQL
      case "postgres" => CREATE_TABLE_POSTGRES_SQL
    }
    
    // why do we still use MySQL which does not even support INDEX IF NOT EXISTS ?...    
    try {      
      val r1 = ctx.executeAction(CREATE_TABLE_SQL)(ExecutionInfo.unknown, ())
      log.info(s"table: ${tableName}: ${r1}")
      val r2 = ctx.executeAction(CREATE_INDEX_SQL)(ExecutionInfo.unknown, ())
      log.info(s"index: ${indexOid}: ${r2}")

      Success(r1)
    } catch {
      case e:Exception => { 
        // short name without full stack (change to check for duplicate index)
        log.warn(s"failed to create: ${e.getMessage()}"); 
        Failure(e) 
      }
    }
  }

  def all(oid:Option[String]):Seq[WalletSecret] = ctx.run(query[WalletSecret])
  //def all:Seq[WalletSecret] = ctx.run(table)

  val deleteById = quote { (addr:String,oid:Option[String]) => 
    query[WalletSecret].filter(o => o.addr == addr && o.oid == oid).delete    
  } 
  //val deleteById = (addr:String) => table.filter(_.addr == lift(addr)).delete

  def +++(w:WalletSecret):Try[WalletSecret] = { 
    log.debug(s"INSERT: ${w}")
    try {
      ctx.run(query[WalletSecret].insertValue(lift(w)));
      //ctx.run(table.insertValue(wal3);
      Success(w)
    } catch {
      case e:Exception => Failure(new Exception(s"could not insert: ${e}"))
    }
  }

  def +(w:WalletSecret):Try[WalletSecret] = +++(w).map(_ => w)
  
  def del(addr:String,oid:Option[String]):Try[WalletSecret] = { 
    log.debug(s"DELETE: addr=${addr},oid=${oid}")
    try {
      ctx.run(deleteById(lift(addr),lift(oid)))
      //ctx.run(deleteById(addr)) 
      match {
        case 0 => Failure(new Exception(s"not found: ${addr}"))
        case _ => Success(WalletSecret("","",addr,oid))
      } 
      
    } catch {
      case e:Exception => Failure(e)
    } 
  }

  def ???(addr:String,oid:Option[String]):Try[WalletSecret] = {
    log.debug(s"SELECT: addr=${addr},oid=${oid}")
    try { 
      ctx.run(query[WalletSecret].filter(w => w.addr == lift(addr))) match {
      //ctx.run(table.filter(w => w.addr == lift(addr))) match {      
        case h :: _ => Success(h)
        case Nil => Failure(new Exception(s"not found: ${addr}"))
      }
    } catch {
      case e:Exception => Failure(e)
    }
  }

  def findByOid(oid:String):Seq[WalletSecret] = {
    log.debug(s"FIND: oid=${oid}")
    //ctx.run(query[WalletSecret].filter(o => o.xid == lift(xid))) match {
    ctx.run(table.filter(w => w.oid == lift(Some(oid))))
  }
  
}