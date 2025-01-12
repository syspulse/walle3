package io.syspulse.wal3

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._
import spray.json.JsObject

case class Wallet(  
  addr:String,
  typ:String,
  ts:Long,
  oid:Option[String],

  // extra data from different signers
  signerData:Option[JsObject] = None
)
