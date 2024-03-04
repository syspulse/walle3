package io.syspulse.wal3.cypher

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger
import scala.concurrent.Future
import scala.collection.immutable
import java.util.Base64

import io.jvm.uuid._

import io.syspulse.skel.crypto.kms.AES
import io.syspulse.skel.util.Util
import io.syspulse.skel.uri.KmsURI

class CypherKMS(uri:String) extends Cypher {
  val log = Logger(s"${this}")

  val kmsUri = KmsURI(uri)
  val keyId:String = kmsUri.uri

  log.info(s"KMS keyId: ${keyId}")

  val aes = new AES(uri)

  def encrypt(data:String):Try[(String,String)] = {    
    val dataEncrypted = aes.encryptBase64(data,keyId)
    Success((dataEncrypted,""))
  }

  def decrypt(data:String,metadata:String):Try[String] = {
    aes.decryptBase64(data,keyId)
  }
}

