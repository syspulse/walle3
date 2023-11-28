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

class WalletSignerEth1(cypher:Cypher) extends WalletSigner {
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

  def sign(ws:WalletSecret,to:String,data:String):Try[String] = {
    for {
      sk <- cypher.decrypt(ws.sk,ws.metadata)
      stix <- Success(Eth.signTransaction(
        sk = sk,
        to = to, 
        value = BigInt(0), 
        nonce = 0, 
        gasPrice = BigInt(0), 
        gasTip = BigInt(0), 
        gasLimit = 21000,
        data = if(data.isBlank) None else Some(data),
        chainId = 11155111
      ))
    } yield stix    
  }
}

