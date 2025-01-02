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

object SecretShare {
  val log = Logger(s"${this}")

  def fromList(shares:String):List[String] = shares.split(",",1024).toList
  
  def fromShare(share:Share):String = s"${share.x}/${share.y}/${Util.hex(share.hash.toArray)}/${share.primeUsed}"
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
  def toShares(shares:List[String]):List[Share] = shares.flatMap(toShare(_))

  def randomShares(num:Int = 1):List[String] = {
    val ss = for(i <- 0 until num) yield 
      Share(
        BigInt(Random.nextInt(1000000000)),
        BigInt(Random.nextInt(2000000000)),
        Random.nextBytes(32).toList,
        Random.nextString(10)
      )
    ss.map(SecretShare.fromShare(_)).toList
  }

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
      k <- Eth.generateRandom()
      shares <- SSS.createShares(k.sk,m,n)
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
  
  def create(oid:Option[String],sk:String):Try[WalletSecret] = {
    log.info(s"create: oid=${oid}, sk=${sk}")    
    for {
      k <- Eth.generate(sk)
      shares <- SSS.createShares(sk,m,n)      
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

  def sign(ws:WalletSecret,
           to:String,nonce:Long,data:String,
           gasPrice:BigInt,gasTip:BigInt,gasLimit:Long,
           value:BigInt = 0,chainId:Long = 11155111):Try[String] = {
    log.info(s"sign: ws=${ws}: chain=${chainId}: to=${to}, nonce=${nonce}, gas=[base:${gasPrice}(${gasPrice.toDouble / 1000000000}gwei),tip:${gasTip}(${gasTip.toDouble / 1000000000}gwei),limit:${gasLimit}], value=${value}, data=${data}")
    
    for {
      web3 <- blockchains.getWeb3(chainId)
      shares <- {
        val shareEncrypted = ws.sk.split(",",1024)
        val shareSeed = ws.metadata.split(",",1024)
        log.debug(s"shareEncrypted: ${shareEncrypted.map(d => s"'${d}'")}")
        log.debug(s"shareSeed: ${shareSeed.map(d => s"'${d}'")}")
        val ee = shareEncrypted.zip(shareSeed).map{ case(share,seed) => {
          cypher.decrypt(share,seed)
        }}
        Try(ee.map(_.get))        
      }
      sk <- {        
        val ss = SecretShare.toShares(shares.toList)
        SSS.getSecret(ss)
      }
      sig <- Eth.signTransaction(
        sk = Util.hex(sk),
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
  }
}

