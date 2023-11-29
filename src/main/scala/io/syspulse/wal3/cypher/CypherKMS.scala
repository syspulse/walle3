package io.syspulse.wal3.cypher

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger
import scala.concurrent.Future
import scala.collection.immutable
import java.util.Base64

import io.jvm.uuid._

import io.syspulse.skel.crypto.kms.AES
import io.syspulse.skel.util.Util

class CypherKMS(keyId:String) extends Cypher {
  val log = Logger(s"${this}")

  log.info(s"KMS keyId: ${keyId}")

  val aes = new AES()

  def encrypt(data:String):Try[(String,String)] = {    
    val dataEncrypted = aes.encryptBase64(data,keyId)
    Success((dataEncrypted,""))
  }

  def decrypt(data:String,metadata:String):Try[String] = {
    aes.decryptBase64(data,keyId)
  }
}

