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
import io.syspulse.wal3.Blockchains

class WalletSignerEth1(cypher:Cypher,blockchains:Blockchains) extends WalletSigner {
  val log = Logger(s"${this}")

  def random(oid:Option[UUID]):Try[WalletSecret] = {
    log.info(s"random keypair: ${oid}")
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

  def sign(ws:WalletSecret,
           to:String,nonce:Long,data:String,
           gasPrice:BigInt,gasTip:BigInt,gasLimit:Long,
           value:BigInt = 0,chainId:Long = 11155111):Try[String] = {
        
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

