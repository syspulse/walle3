package io.syspulse.wal3.store

import scala.jdk.CollectionConverters._

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

// import software.amazon.awssdk.regions.Region;
import com.amazonaws.services.kms.{AWSKMS, AWSKMSClientBuilder}
import com.amazonaws.services.kms.model.DecryptRequest
import com.amazonaws.services.kms.model.EncryptRequest
import com.amazonaws.services.kms.model.GenerateDataKeyRequest

import io.syspulse.wal3.{Wallet}
import io.syspulse.wal3.WalletSecret
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.kms.model.CreateKeyRequest
import com.amazonaws.services.kms.model.CustomerMasterKeySpec
import com.amazonaws.services.kms.model.DisableKeyRequest
import com.amazonaws.services.kms.model.ScheduleKeyDeletionRequest
import com.amazonaws.services.kms.model.ListKeysRequest
import com.amazonaws.services.kms.model.GetPublicKeyRequest
import io.syspulse.skel.util.Util
import com.amazonaws.services.kms.model.Tag
import com.amazonaws.services.kms.model.CreateAliasRequest
import com.amazonaws.services.kms.model.ListAliasesRequest
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.x9.ECNamedCurveTable
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.asn1.sec.SECObjectIdentifiers
import java.nio.ByteBuffer

class WalletStoreKMS extends WalletStore {
  val log = Logger(s"${this}")
  
  val region = sys.env.get("AWS_REGION").getOrElse("")
  val account = sys.env.get("AWS_ACCOUNT").getOrElse("")

  val kms = AWSKMSClientBuilder
    .standard
    .withEndpointConfiguration(new EndpointConfiguration(sys.env.get("AWS_ENDPOINT").getOrElse(""),region))
    .build
    

  //def keyId(addr:String) = s"arn:aws:kms:${region}:${account}:key/1594b719-4d64-4581-b682-8bd4a94d2a30"

  def all(oid:Option[UUID]):Seq[WalletSecret] = 
    if(oid==None) 
      Seq.empty
    else 
      Seq.empty[WalletSecret].filter(_.oid == oid).toSeq

  def size:Long = all(None).size

  def findByOid(oid:UUID):Seq[WalletSecret] = 
    all(Some(oid)).filter(_.oid == Some(oid)).toSeq

  def alias(addr:String,oid:Option[UUID]) = s"alias/${addr.toLowerCase()}/${if(oid.isDefined) oid.get.toString else ""}"

  //val pkBytes =  KeyFactory.getInstance("ECDSA").generatePublic(new X509EncodedKeySpec(der)).getEncoded()
  //val pkBytes = SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(der)).parsePublicKey().getEncoded()
            
  // val algId = new AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256k1);
  // val subjectPublicKeyInfo = new SubjectPublicKeyInfo(algId, der);
  // val pkBytes = subjectPublicKeyInfo.getEncoded("DER")
          
  // The first 23 bytes are just ASN.1 stuff. The remaining 65 bytes contain the uncompressed public key.
  // The first hexadecimal character is 04 (to signify a 65-byte uncompressed public key), followed by the 32-byte X value, followed by the 32-byte Y value         
  def extractPK(b:ByteBuffer) = {
    b.rewind()
    val der = new Array[Byte](b.remaining())          
    b.get(der)

    val pkCompression = der.drop(23).take(1)
    val pkBytes = der.drop(23).drop(1)

    log.debug(s"Compression: ${Util.hex(pkCompression)}: PK: ${Util.hex(pkBytes)}")

    pkBytes
  }

  def +++(w:WalletSecret):Try[WalletSecret] = {     
    for {
      keyId <- {
        val req = new CreateKeyRequest()
          .withDescription("wal3-key")      
          .withCustomerMasterKeySpec(CustomerMasterKeySpec.ECC_SECG_P256K1)
          .withKeyUsage("SIGN_VERIFY")
          .withTags(
            new Tag()
              .withTagKey("oid")
              .withTagValue(if(w.oid.isDefined) w.oid.get.toString else ""),
            new Tag()
              .withTagKey("addr")
              .withTagValue(w.addr.toLowerCase())
          )
      
        try {
          var res = kms.createKey(req)
          val arn = res.getKeyMetadata().getArn()
          Success(
            res.getKeyMetadata().getKeyId()
          )          
        } catch {
          case e:Exception =>
            log.error("",e)
            Failure(new Exception(s"failed to create: ${w.addr}",e))
        }
      }
      pk <- {
        val req = new GetPublicKeyRequest().withKeyId(keyId)
        try { 
          val res = kms.getPublicKey(req)
          val pkBytes = extractPK(res.getPublicKey())
          Success(
            Util.hex(pkBytes)
          )
        } catch {
          case e:Exception => 
            log.error("",e)
            Failure(new Exception(s"failed to get PK: ${keyId}",e))
        }
      }

      alias <- {
        val req = new CreateAliasRequest()
          .withTargetKeyId(keyId)
          .withAliasName(alias(w.addr,w.oid))
        
        try {
          val res = kms.createAlias(req)
          log.info(s"Alias: ${w.addr}: ${req.getAliasName()}")
          
          Success(req.getAliasName())

        } catch {
          case e:Exception =>
            log.error("",e)
            Failure(new Exception(s"failed to create: ${w.addr}",e))
        }
      }

      w1 <- {
        // copy keyId to metadata
        val w1 = w.copy(pk = pk, metadata = keyId)        
        Success(w1)
      }
    } yield w1
  }

  def +(w:WalletSecret):Try[WalletStoreKMS] = +++(w).map(_ => this)

  def del(addr:String,oid:Option[UUID]):Try[WalletSecret] = {         
    ???(addr,oid) match {
      case Success(w) if w.oid == oid =>        
        val req = new ScheduleKeyDeletionRequest()
          .withPendingWindowInDays(7)
          .withKeyId(w.metadata)
          Success(w)                      
      case _ => 
        Failure(new Exception(s"not found: ${addr}"))
    }
  }

  case class KeyData(keyId:String,addr:String,oid:Option[UUID])
  
  def ???(addr:String,oid:Option[UUID]):Try[WalletSecret] = {
    // get ALL aliases
    // var marker = "_"
    // var keys = Seq[String]()
    // while( marker != "" ) {
    //   val req0 = new ListKeysRequest().withLimit(1000)
    //   val res0 = kms.listKeys(req0)
    //   keys = keys ++ res0.getKeys().asScala.toList.map( e => e.getKeyId())
    //   if(res0.getTruncated())
    //     marker = res0.getNextMarker()
    //   else 
    //     marker = ""
    // }
    var marker = "_"
    var keys = Seq[KeyData]()
    var found:Option[KeyData] = None

    while( marker != "" && found == None ) {
      val req0 = new ListAliasesRequest().withLimit(100)
      val res0 = try {
        kms.listAliases(req0)
      } catch {
        case e:Exception => 
          return Failure(new Exception(s"not found: ${addr}",e))
      }
      
      val keys0 = res0.getAliases.asScala.toList.map( a => 
        a.getAliasName().split("/").toList match {
          case "alias" :: addr :: Nil => 
            KeyData(a.getTargetKeyId,addr,None)
          case "alias" :: addr :: "" :: Nil => 
            KeyData(a.getTargetKeyId,addr,None)
          case "alias" :: addr :: oid :: Nil =>
            KeyData(a.getTargetKeyId,addr,Some(UUID(oid)))
          case _ => 
            log.error(s"Invalid Alias: ${a}")
            return Failure(new Exception(s"not found: ${addr}: invalid alias: ${a}"))
        }        
      )

      log.info(s"keys0: ${keys0}")
      keys = keys ++ keys0

      found = keys0.find(ka => ka.addr == addr.toLowerCase())

      if(res0.getTruncated())
        marker = res0.getNextMarker()
      else 
        marker = ""
    }
    // use keys to update cache
    // >>>>>>>>>>>>>>>>>>>>

    if(!found.isDefined) {      
      return Failure(new Exception(s"not found: ${addr}"))
    }

    if(oid != None && found.get.oid != oid) {
      return Failure(new Exception(s"not found: ${addr} (oid access)"))
    }

    val req = new GetPublicKeyRequest().withKeyId(found.get.keyId)
    try { 
      val res = kms.getPublicKey(req)      
      // case Some(w) if(w.oid == oid) => Success(w)
      
      val pkBytes = extractPK(res.getPublicKey())          
      Success(WalletSecret(
        sk = "",
        pk = Util.hex(pkBytes),
        addr = addr,
        oid = oid
      ))
    } catch {
      case e:Exception => 
        log.error("",e)
        Failure(new Exception(s"not found: ${addr}",e))
    }
  }
 
}
