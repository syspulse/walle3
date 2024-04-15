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

class WalletSignerEth1(cypher:Cypher,blockchains:Blockchains) extends WalletSigner {
  val log = Logger(s"${this}")

  def random(oid:Option[String]):Try[WalletSecret] = {
    log.info(s"random: oid=${oid}")
    for {
      kp <- Eth.generateRandom()
      (sk,seed) <- cypher.encrypt(Util.hex(kp.sk))
      ws <- Success(WalletSecret(
        sk,
        Util.hex(kp.pk),
        Eth.address(kp.pk),
        oid,
        metadata = seed
      ))
     } yield ws
  }

  def create(oid:Option[String],sk:String):Try[WalletSecret] = {
    log.info(s"create: oid=${oid}, sk_hash=${Util.sha256(sk)}")
    for {
      kp <- Eth.generate(sk)
      (sk,seed) <- cypher.encrypt(Util.hex(kp.sk))
      ws <- Success(WalletSecret(
        sk,
        Util.hex(kp.pk),
        Eth.address(kp.pk),
        oid,
        metadata = seed
      ))
     } yield ws
  }

  def sign(ws:WalletSecret,
           to:String,nonce:Long,data:String,
           gasPrice:BigInt,gasTip:BigInt,gasLimit:Long,
           value:BigInt = 0,chainId:Long = 11155111):Try[String] = {
    log.info(s"sign: ws=${ws}: [${chainId}]: to=${to},nonce=${nonce},gas=${gasPrice}(${gasPrice / 1000000000L}gwei)/${gasTip}(${gasTip / 1000000000L}gwei)/${gasLimit},value=${value},data=${data}")
    for {
      web3 <- blockchains.getWeb3(chainId)
      sk <- cypher.decrypt(ws.sk,ws.metadata)
      sig <- Eth.signTransaction(
        sk = sk,
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

