package io.syspulse.wal3.cypher

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.crypto.AES
import io.syspulse.skel.util.Util

class CypherNone(prefix:String) extends Cypher {
  val aes = new AES()

  def encrypt(data:String):Try[(String,String)] = {    
    Success((prefix + data,""))
  }

  def decrypt(data:String,metadata:String):Try[String] = {
    Success(data.drop(prefix.size))
  }
}

