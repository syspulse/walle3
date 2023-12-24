package io.syspulse.wal3.signer

import scala.jdk.CollectionConverters._
import scala.util.{Try,Success,Failure}
import scala.concurrent.Future
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

import io.syspulse.skel.crypto.Eth
import io.syspulse.wal3.WalletSecret
import io.syspulse.skel.util.Util
import org.web3j.protocol.core.methods.response.EthSign
import io.syspulse.wal3.cypher.Cypher
import io.syspulse.wal3.Blockchains
import org.web3j.crypto.RawTransaction
import java.math.BigInteger
import org.web3j.crypto.TransactionEncoder
import com.amazonaws.services.kms.model.SignRequest
import org.web3j.crypto.Sign
import org.web3j.crypto.ECDSASignature
import org.web3j.crypto.Hash

case class KeyData(keyId:String,addr:String,oid:Option[UUID])

abstract class WalletSignerKMS(blockchains:Blockchains,uri:String = "",tag:String = "") extends WalletSigner {
  val log = Logger(s"${this}")

  val CYPHER = "KMS"
  val region = sys.env.get("AWS_REGION").getOrElse("")
  val account = sys.env.get("AWS_ACCOUNT").getOrElse("")

  val kms0 = AWSKMSClientBuilder
    .standard

  val kms = (
    if(!uri.isEmpty())
      kms0.withEndpointConfiguration(new EndpointConfiguration(uri,region))
    else if(sys.env.get("AWS_ENDPOINT").isDefined)
      kms0.withEndpointConfiguration(new EndpointConfiguration(sys.env.get("AWS_ENDPOINT").get,region))
    else
      kms0
  )
  .build

  log.info(s"KMS(${uri}): ${kms}")  

  def random(oid:Option[UUID]):Try[WalletSecret] = {
    log.info(s"random key: ${oid}")
    create(oid)
  }

  def create(oid:Option[UUID],sk:String):Try[WalletSecret] = {
    Failure(new Exception(s"KMS create from SK is not supported: ${oid}"))
  }

  // ---- KMS --------------------------------------------------------------------------------------------------------------

  def alias(addr:String,oid:Option[UUID]) = s"alias/${addr.toLowerCase()}/${if(oid.isDefined) oid.get.toString else ""}"

  //val pkBytes =  KeyFactory.getInstance("ECDSA").generatePublic(new X509EncodedKeySpec(der)).getEncoded()
  //val pkBytes = SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(der)).parsePublicKey().getEncoded()
            
  // val algId = new AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256k1);
  // val subjectPublicKeyInfo = new SubjectPublicKeyInfo(algId, der);
  // val pkBytes = subjectPublicKeyInfo.getEncoded("DER")
          
  // The first 23 bytes are just ASN.1 stuff. The remaining 65 bytes contain the uncompressed public key.
  // The first hexadecimal character is 04 (to signify a 65-byte uncompressed public key), followed by the 32-byte X value, followed by the 32-byte Y value         
  def extractBytes(b:ByteBuffer) = {
    b.rewind()
    val der = new Array[Byte](b.remaining())          
    b.get(der)

    val pkCompression = der.drop(23).take(1)
    val pkBytes = der.drop(23).drop(1)

    log.debug(s"Compression: ${Util.hex(pkCompression)}: PK: ${Util.hex(pkBytes)}")

    pkBytes
  }

  def create(oid:Option[UUID]):Try[WalletSecret] = {
    for {
      keyId <- {
        val req = new CreateKeyRequest()
          .withDescription("wal3-key")
          .withCustomerMasterKeySpec(CustomerMasterKeySpec.ECC_SECG_P256K1)
          .withKeyUsage("SIGN_VERIFY")
          .withTags(
            new Tag()
              .withTagKey("oid")
              .withTagValue(if(oid.isDefined) oid.get.toString else ""),
            new Tag()
              .withTagKey("system")
              .withTagValue(tag)
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
            Failure(new Exception(s"failed to create: oid=${oid}",e))
        }
      }
      pk <- {
        val req = new GetPublicKeyRequest().withKeyId(keyId)
        try { 
          val res = kms.getPublicKey(req)
          val pkBytes = extractBytes(res.getPublicKey())
          Success(
            Util.hex(pkBytes)
          )
        } catch {
          case e:Exception => 
            log.error("",e)
            Failure(new Exception(s"failed to get PK: ${keyId}",e))
        }
      }
      addr <- Success(Eth.address(pk))
      alias <- {
        val req = new CreateAliasRequest()
          .withTargetKeyId(keyId)
          .withAliasName(alias(addr,oid))
        
        try {
          val res = kms.createAlias(req)
          log.debug(s"alias: ${addr}: ${req.getAliasName()}")
          
          Success(req.getAliasName())
        } catch {
          case e:Exception =>
            log.error("",e)
            Failure(new Exception(s"failed to create: ${addr}",e))
        }
      }

      w1 <- {        
        val w1 = WalletSecret(
          sk = "",
          pk = pk, 
          addr = addr,
          oid = oid,
          typ = "ECDSA",
          cypher = CYPHER,
          metadata = keyId
        )
        Success(w1)
      }
    } yield w1
  }

  def sign(ws:WalletSecret,
           to:String,nonce:Long,data:String,
           gasPrice:BigInt,gasTip:BigInt,gasLimit:Long,
           value:BigInt = 0,chainId:Long = 11155111):Try[String] = {
    
    val rawTx: RawTransaction = 
      RawTransaction.createTransaction(
        chainId,
        BigInteger.valueOf(nonce), 
        BigInteger.valueOf(gasLimit), 
        to,            
        value.bigInteger,
        data,
        gasTip.bigInteger,
        gasPrice.bigInteger
      )
        
    val encodedTransaction = TransactionEncoder.encode(rawTx)

    log.info(s"Encoded TX: ${Util.hex(encodedTransaction)}")

    val keyId = ws.metadata    
    
    for {
      sigVRS <- {
        try {
          val digest = Hash.sha3(encodedTransaction)
          log.info(s"Encoded TX hash: ${Util.hex(digest)}")

          val req = new SignRequest()
            .withKeyId(keyId)
            .withSigningAlgorithm("ECDSA_SHA_256")
            .withMessageType("DIGEST")
            .withMessage(ByteBuffer.wrap(digest))
          
          val res = kms.sign(req)
          
          val b = res.getSignature()
          b.rewind()
          val sigDer = new Array[Byte](b.remaining())          
          b.get(sigDer)
              
          log.info(s"DER sig: ${Util.hex(sigDer)} (${(Util.hex(sigDer).size-2)/2})")
          // echo "MEUCIBZKDXa2ddxjOsv1CnfwF/A5/DBsfLBS8DwJfTKQYNrgAiEAs80CvDRgv0Q14Qzx3Geb8OOnVDvK/epZWKeliq1Bqks=" | base64 -d | hexdump -C
          // 00000000  30 45 02 20 16 4a 0d 76  b6 75 dc 63 3a cb f5 0a  |0E. .J.v.u.c:...|
          // 00000010  77 f0 17 f0 39 fc 30 6c  7c b0 52 f0 3c 09 7d 32  |w...9.0l|.R.<.}2|
          // 00000020  90 60 da e0 02 21 00 b3  cd 02 bc 34 60 bf 44 35  |.`...!.....4`.D5|
          // 00000030  e1 0c f1 dc 67 9b f0 e3  a7 54 3b ca fd ea 59 58  |....g....T;...YX|
          // 00000040  a7 a5 8a ad 41 aa 4b                              |....A.K|
          // 00000047
          // echo "MEUCIBZKDXa2ddxjOsv1CnfwF/A5/DBsfLBS8DwJfTKQYNrgAiEAs80CvDRgv0Q14Qzx3Geb8OOnVDvK/epZWKeliq1Bqks=" | base64 -d >sig1.bin        
          // openssl asn1parse -inform DER -in sig1.bin 
          //  0:d=0  hl=2 l=  69 cons: SEQUENCE          
          //  2:d=1  hl=2 l=  32 prim: INTEGER           :164A0D76B675DC633ACBF50A77F017F039FC306C7CB052F03C097D329060DAE0
          // 36:d=1  hl=2 l=  33 prim: INTEGER           :B3CD02BC3460BF4435E10CF1DC679BF0E3A7543BCAFDEA5958A7A58AAD41AA4B
          
          //  30 45 02 20
          val rSize = sigDer.drop(3).take(1)(0).toInt
          val r = sigDer.drop(4).take(rSize)
          // 02 21
          val sSize = sigDer.drop(4).drop(rSize).drop(1).take(1)(0).toInt
          val s = sigDer.drop(4).drop(rSize).drop(1).drop(1).take(sSize)

          log.info(s"\nr=${Util.hex(r)}\ns=${Util.hex(s)}\npk=${ws.pk} (${(ws.pk.size-2)/2})")
          
          val ecdsaSig = new ECDSASignature(new java.math.BigInteger(r),new java.math.BigInteger(s))
          //val publicKey = new java.math.BigInteger(Util.fromHexString(ws.pk))
          val publicKey = new java.math.BigInteger(Eth.normalize(Util.fromHexString(ws.pk),65))
          val messageHash = digest//Hash.sha3(encodedTransaction)

          val sigData = Sign.createSignatureData(ecdsaSig,publicKey,messageHash)
          
          val sigVRS = TransactionEncoder.encode(rawTx,sigData)
          log.info(s"sig: ${Util.hex(sigVRS)}")

          Success(sigVRS)
        } catch {
          case e:Exception =>
            log.error(s"",e)
            Failure(new Exception(s"could not sign: ${ws.addr}",e))
        }
      }
      sig <- {
        Success(Util.hex(sigVRS))
        // try {
        //   val data = TransactionEncoder.encode(rawTx, signatureData)
        //   Success(data)
        // } catch {
        //   case e:Exception => 
        //     Failure(new Exception(s"could not sign: ${ws.addr}",e))
        // }
      }
    } yield sig    
  }

  def list(addr:Option[String] = None,oid:Option[UUID] = None):Seq[WalletSecret] = {    
    var marker = "_"
    var keys = Seq[KeyData]()
    var found:Option[KeyData] = None

    while( marker != "" && found == None ) {
      val req0 = new ListAliasesRequest().withLimit(100)
      val res0 = try {
        kms.listAliases(req0)
      } catch {
        case e:Exception => 
          log.warn(s"failed to list: ${addr}",e)
          return Seq()
      }
      
      val keys0 = res0.getAliases.asScala.toList.flatMap( a => 
        a.getAliasName().split("/").toList match {
          case "alias" :: addr :: Nil => 
            Some(KeyData(a.getTargetKeyId,addr,None))
          case "alias" :: addr :: "" :: Nil => 
            Some(KeyData(a.getTargetKeyId,addr,None))
          case "alias" :: addr :: oid :: Nil =>
            Some(KeyData(a.getTargetKeyId,addr,Some(UUID(oid))))
          case _ => 
            log.warn(s"Invalid Alias: ${a}")
            None
        }        
      )

      log.debug(s"keys0: ${keys0}")

      val keys1 = if(oid.isDefined) 
        keys0.filter(_.oid == oid)
      else
        keys0

      log.debug(s"keys1: ${keys1}")

      keys = keys ++ keys1

      found = if(addr.isDefined) 
        keys0.find(ka => ka.addr == addr.get.toLowerCase())
      else
        None      

      if(res0.getTruncated())
        marker = res0.getNextMarker()
      else 
        marker = ""
    }
    // use keys to update cache
    // >>>>>>>>>>>>>>>>>>>>

    if(addr.isDefined){
      if(!found.isDefined)
        return Seq.empty
      
      if(oid != None && found.get.oid != oid) {
        log.error(s"${addr}: oid access")
        return Seq.empty
      }

      keys = Seq(found.get)
    }    

    keys.flatMap( key => {
      val req = new GetPublicKeyRequest().withKeyId(key.keyId)
      try { 
        val res = kms.getPublicKey(req)        
        
        val pkBytes = extractBytes(res.getPublicKey())          
        
        Some(WalletSecret(
          sk = "",
          pk = Util.hex(pkBytes),
          addr = key.addr,
          oid = key.oid,

          cypher = CYPHER,
          metadata =key.keyId
        ))

      } catch {
        case e:Exception => 
          log.warn("failed to get PK",e)
          None
      }
    })
  }
}

