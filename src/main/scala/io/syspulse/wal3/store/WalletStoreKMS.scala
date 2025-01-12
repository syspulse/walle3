package io.syspulse.wal3.store

import scala.jdk.CollectionConverters._

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import com.amazonaws.services.kms.{AWSKMS, AWSKMSClientBuilder}
import com.amazonaws.services.kms.model.DecryptRequest
import com.amazonaws.services.kms.model.EncryptRequest
import com.amazonaws.services.kms.model.GenerateDataKeyRequest
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.kms.model.CreateKeyRequest
import com.amazonaws.services.kms.model.CustomerMasterKeySpec
import com.amazonaws.services.kms.model.DisableKeyRequest
import com.amazonaws.services.kms.model.ScheduleKeyDeletionRequest
import com.amazonaws.services.kms.model.ListKeysRequest
import com.amazonaws.services.kms.model.GetPublicKeyRequest
import com.amazonaws.services.kms.model.Tag
import com.amazonaws.services.kms.model.CreateAliasRequest
import com.amazonaws.services.kms.model.ListAliasesRequest
import com.amazonaws.services.kms.model.DeleteAliasRequest

import java.nio.ByteBuffer

import io.syspulse.wal3.{Wallet}
import io.syspulse.wal3.WalletSecret
import io.syspulse.skel.util.Util

import io.syspulse.skel.crypto.Eth
import io.syspulse.blockchain.Blockchains
import io.syspulse.wal3.signer.WalletSignerKMS
import io.syspulse.wal3.signer.SignerSecret

class WalletStoreKMS(blockchains:Blockchains = Blockchains(),uri:String = "",tag:String = "") 
     extends WalletSignerKMS(blockchains,uri,tag) 
     with WalletStore {
  
  def id:String = "kms"
  
  def all(oid:Option[String]):Seq[WalletSecret] = list(None,oid)    

  def size:Long = all(None).size

  def findByOid(oid:String):Seq[WalletSecret] = 
    all(Some(oid)).filter(_.oid == Some(oid)).toSeq

  def +++(s:SignerSecret):Try[SignerSecret] = create(s.ws.oid).map(_ => s)

  def +(w:WalletSecret):Try[WalletSecret] = create(w.oid).map(_ => w)

  def del(addr:String,oid:Option[String]):Try[WalletSecret] = {         
    for {
      w <- ???(addr,oid)
      _ <- {
          val req = new ScheduleKeyDeletionRequest()
            .withPendingWindowInDays(7)
            .withKeyId(w.metadata)
        
          try {
            val res = kms.scheduleKeyDeletion(req)
            log.info(s"${addr}: key(${res.getKeyId()}): schedule delete: '${res.getDeletionDate()}'")
            Success(w)

          } catch {
            case e:Exception =>
              Failure(e)
          }
        }
      _ <- {
        // delete alias
        val req = new DeleteAliasRequest()
          .withAliasName(alias(w.addr,w.oid))
              
        try {
          val res = kms.deleteAlias(req)
          Success(w)

        } catch {
          case e:Exception =>
            Failure(e)
        }
      }        
    } yield w
  }
    
  def ???(addr:String,oid:Option[String]):Try[WalletSecret] = {    
    list(Some(addr),oid).toList match {
      case w :: _ => 
        Success(w)
      case Nil =>
        Failure(new Exception(s"not found: ${addr}"))
    }
  }
}
