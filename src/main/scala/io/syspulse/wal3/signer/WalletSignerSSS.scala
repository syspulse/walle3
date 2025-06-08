package io.syspulse.wal3.signer

import scala.util.Random

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import spray.json._
import spray.json.JsObject

import org.web3j.protocol.core.methods.response.EthSign

import io.syspulse.skel.crypto.Eth
import io.syspulse.wal3.WalletSecret
import io.syspulse.skel.util.Util
import io.syspulse.wal3.cypher.Cypher
import io.syspulse.skel.blockchain.Blockchains

import io.syspulse.skel.crypto.SSS
import org.secret_sharing.Share

// user shares
case class SignerSSSUserShare(shares:Seq[String]) extends SignerData

object WalletSignerSSSJson extends DefaultJsonProtocol {
  implicit val jf_signer_sss_user_share = jsonFormat1(SignerSSSUserShare)
}

object SecretShare {
  val log = Logger(s"${this}")

  def fromList(shares:String):List[String] = shares.split(",",1024).map(_.trim).toList
  def toList(shares:List[String]):String = shares.mkString(",")
  
  def fromShare(share:Share):String = s"${share.x}/${share.y}/${Util.hex(share.hash.toArray)}/${share.primeUsed}"
  def fromShares(shares:List[Share]):List[String] = shares.map(fromShare(_))
  
  def toShare(s:String):Option[Share] = {
    s.split("/") match {
      case Array(x,y,hash,primeUsed) =>
        Some(Share(BigInt(x),BigInt(y),Util.fromHexString(hash).toList,primeUsed))
      case _ => 
        log.error(s"invalid share: ${s}")
        None
    }
  }
  def toShares(shares:String):List[Share] = fromList(shares).flatMap(toShare(_))
  def toShares(shares:Seq[String]):List[Share] = shares.toList.flatMap(toShare(_))

  // def randomShares(num:Int = 1):List[String] = {
  //   val ss = for(i <- 0 until num) yield 
  //     Share(
  //       BigInt(Random.nextInt(1000000000)),
  //       BigInt(Random.nextInt(2000000000)),
  //       Random.nextBytes(32).toList,
  //       Random.nextString(10)
  //     )
  //   ss.map(SecretShare.fromShare(_)).toList
  // }

  def apply(s:String) = toShares(s)
}

class WalletSignerSSS(cypher:Cypher,uri:String,blockchains:Blockchains) extends WalletSigner {
  val log = Logger(s"${this}")

  import WalletSignerSSSJson._

  val (m,n) = uri.split(":").toList match {
    case n :: Nil => (n.toInt - 1,n.toInt)
    case m :: n :: Nil => (m.toInt,n.toInt)
    case _ => (3,5)
  }

  val DEF_TYPE = "sss"
  val DEF_ALGO = "ECDSA"
  val DEF_CURVE = "secp256k1" // default

  def toType:String = s"${DEF_TYPE}:${m}:${n}:${DEF_ALGO}:${DEF_CURVE}"
  def fromType(t:String):(String,Int,Int,String,String) = t.split(":").toList match {
    case DEF_TYPE :: m :: n :: Nil => (t,m.toInt,n.toInt,DEF_ALGO,DEF_CURVE)
    case DEF_TYPE :: Nil => (DEF_TYPE,2,3,DEF_ALGO,DEF_CURVE)
    case "" :: Nil => (DEF_TYPE,2,3,DEF_ALGO,DEF_CURVE)
    case t :: Nil => (t,0,0,DEF_ALGO,DEF_CURVE)    
  }

  def random(oid:Option[String]):Try[SignerSecret] = {
    log.info(s"random: oid=${oid}: ${toType}")
    
    for {
      
      //k <- Eth.generateRandom()
      k <- Eth.random()
      //shares <- SSS.createShares(k.sk,m,n)
      shares <- SSS.createShares(Util.hex(k.sk),m,n)

      encrypted <- {
        log.debug(s"shares=${shares}")
        
        val sharesEncoded = shares.map(s => SecretShare.fromShare(s))
        log.info(s"shares=${sharesEncoded}")

        val ss = sharesEncoded.map(s => cypher.encrypt(s))        
        Try(ss.map(_.get))
      }
      ws <- {
        log.debug(s"shareEncrypted: ${encrypted.map(d => s"'${d._1}'")}")
        log.debug(s"shareSeed: ${encrypted.map(d => s"'${d._2}'")}")
        Success(WalletSecret(
          sk = encrypted.map(_._1).mkString(","),
          pk = Util.hex(k.pk),
          addr = Eth.address(k.pk),
          oid,
          typ = toType,
          metadata = encrypted.map(_._2).mkString(",")
        ))
      }
     } yield SignerSecret(ws,None)
  }
  
  def create(oid:Option[String],sk:String):Try[SignerSecret] = {
    log.info(s"create: oid=${oid}, sk=${sk}")    
    for {
      
      k <- Eth.generate(sk)      
      //shares <- SSS.createShares(sk,m,n)
      shares <- SSS.createShares(Util.hex(k.sk),m,n)      
      
      encrypted <- {
        log.debug(s"shares=${shares}")
        
        val sharesEncoded = shares.map(s => SecretShare.fromShare(s))
        log.info(s"shares=${sharesEncoded}")

        val ss = sharesEncoded.map(s => cypher.encrypt(s))        
        Try(ss.map(_.get))
      }
      ws <- {
        log.debug(s"shareEncrypted: ${encrypted.map(d => s"'${d._1}'")}")
        log.debug(s"shareSeed: ${encrypted.map(d => s"'${d._2}'")}")
        Success(WalletSecret(
          sk = encrypted.map(_._1).mkString(","),
          pk = Util.hex(k.pk),
          addr = Eth.address(k.pk),
          oid,
          typ = toType,
          metadata = encrypted.map(_._2).mkString(",")
        ))
      }
    } yield SignerSecret(ws,None)
  }

  def sign(ss:SignerSecret, payload:SignerPayload):Try[String] = {
    payload match {
      case SignerTxPayload(to,nonce,data,gasPrice,gasTip,gasLimit,value,chainId) =>
        val ws = ss.ws
        val userShares = ss.data match {
          case Some(SignerSSSUserShare(userShares)) =>
            // decode user shares
            userShares
          case _ =>
            Seq()
        }

        log.info(s"sign: userShares=${userShares}, ws=${ws}, chain=${chainId}: to=${to}, nonce=${nonce}, gas=[base:${gasPrice}(${gasPrice.toDouble / 1000000000}gwei),tip:${gasTip}(${gasTip.toDouble / 1000000000}gwei),limit:${gasLimit}], value=${value}, data=${data}")
        
        for {
          //web3 <- blockchains.getWeb3(chainId)
          shares <- {
            val shareEncrypted = SecretShare.fromList(ws.sk)
            val shareSeed = SecretShare.fromList(ws.metadata)
            log.info(s"shareEncrypted: ${shareEncrypted.map(d => s"'${d}'")}")
            log.info(s"shareSeed: ${shareSeed.map(d => s"'${d}'")}")
            val ee = shareEncrypted.zip(shareSeed).map{ case(share,seed) => {
              cypher.decrypt(share,seed)
            }}
            Try(ee.map(_.get))
          }
          sk <- {            
            val ss = SecretShare.toShares(shares ++ userShares)            
            SSS.getSecret(ss)
          }
          sig <- Eth.signTransaction(
            sk = new String(sk),
            to = to, 
            value = value, 
            nonce = nonce, 
            gasPrice = gasPrice, 
            gasTip = gasTip, 
            gasLimit = gasLimit,
            data = if(data.isBlank) None else Some(data),
            chainId = chainId
          )
        } yield sig
      
      case _ =>
        Failure(new Exception(s"Unsupported payload: ${payload}"))
    }
  }

  override def decodeSignerData(signerType:Option[String],signerData:Option[JsObject]):Option[SignerData] = {
    signerType.map(fromType(_)) match {
      case Some(("sss",_,_,_,_)) =>
        signerData match {
          case Some(data) =>
            // parse obj
            Some(data.convertTo[SignerSSSUserShare])
          case _ =>
            log.warn(s"failed to parse data: ${signerType.get}")
            None
        }
      case _ => 
        log.warn(s"unsupported type: '${signerType.getOrElse("")}'")
        None
    }
  }

  override def encodeSignerData(ss:SignerSecret):Option[JsObject] = {
    ss.data match {
      case Some(SignerSSSUserShare(shares)) =>
        Some(
          SignerSSSUserShare(shares).toJson.asJsObject
        )
      case _ =>
        log.warn(s"unknown signerData: ${ss.data}")
        None
    }
  }

  def sign712(ss:SignerSecret, message:String):Try[String] = {
    Failure(new Exception(s"KMS sign712 is not supported: ${ss}"))
  }
}


