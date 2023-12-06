package io.syspulse.wal3.cypher

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.crypto.AES
import io.syspulse.skel.util.Util

class CypherFile(file:String) extends CypherKey("") {
  val pass = os.read(os.Path(file,os.pwd))
  
  override def getPass():String = pass
}

