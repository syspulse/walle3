package io.syspulse.wal3.cypher

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.crypto.AES
import io.syspulse.skel.util.Util

class CypherPass(pass:String) extends Cypher {
  val aes = new AES()

  def encrypt(data:String):Try[(String,String)] = {    
    val seed = aes.generateSeedRandom()
    val dataEncrypted = aes.encryptBase64(data,pass,Some(seed))
    Success((dataEncrypted,seed))
  }

  def decrypt(data:String,metadata:String):Try[String] = {
    val seed = metadata
    aes.decryptBase64(data,pass,Some(seed))
  }
}

