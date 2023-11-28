package io.syspulse.wal3.cypher

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

trait Cypher {    
  def encrypt(data:String):Try[(String,String)]
  def decrypt(data:String,metadata:String):Try[String]
}

