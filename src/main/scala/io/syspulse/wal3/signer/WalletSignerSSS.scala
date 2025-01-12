package io.syspulse.wal3.signer

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import io.syspulse.skel.crypto.Eth
import io.syspulse.wal3.WalletSecret
import io.syspulse.skel.util.Util
import org.web3j.protocol.core.methods.response.EthSign
import io.syspulse.wal3.cypher.Cypher
import io.syspulse.blockchain.Blockchains

import io.syspulse.skel.crypto.SSS
import org.secret_sharing.Share
import scala.util.Random

// user shares
case class SignerSSSUserShare(shares:Seq[String]) extends SignerData

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

  val (m,n) = uri.split(":").toList match {
    case n :: Nil => (n.toInt - 1,n.toInt)
    case m :: n :: Nil => (m.toInt,n.toInt)
    case _ => (3,5)
  }

  // val DEF_TYPE = "sss:ECDSA:secp256k1"
  def toType:String = s"sss:${m}:${n}:ECDSA:secp256k1"

  def random(oid:Option[String]):Try[WalletSecret] = {
    log.info(s"random: oid=${oid}: ${toType}")
    
    for {
      
      //k <- Eth.generateRandom()
      k <- Eth.random()
      //shares <- SSS.createShares(k.sk,m,n)
      shares <- SSS.createShares(Util.hex(k.sk),m,n)

      encrypted <- {
        log.info(s"shares=${shares}")
        val ss = shares.map(s => cypher.encrypt(SecretShare.fromShare(s)))        
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
     } yield ws
  }
  
  def create(oid:Option[String],sk:String):Try[WalletSecret] = {
    log.info(s"create: oid=${oid}, sk=${sk}")    
    for {
      
      k <- Eth.generate(sk)      
      //shares <- SSS.createShares(sk,m,n)
      shares <- SSS.createShares(Util.hex(k.sk),m,n)      
      
      encrypted <- {
        log.debug(s"shares=${shares}")
        val ss = shares.map(s => cypher.encrypt(SecretShare.fromShare(s)))
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
    } yield ws
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

        log.info(s"sign: ws=${ws}: userShares=${userShares}: chain=${chainId}: to=${to}, nonce=${nonce}, gas=[base:${gasPrice}(${gasPrice.toDouble / 1000000000}gwei),tip:${gasTip}(${gasTip.toDouble / 1000000000}gwei),limit:${gasLimit}], value=${value}, data=${data}")
        
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
}


