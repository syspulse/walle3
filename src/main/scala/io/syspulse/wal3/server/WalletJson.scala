package io.syspulse.wal3.server

import io.syspulse.skel.service.JsonCommon

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.wal3._
import io.syspulse.wal3.server._

object WalletJson extends JsonCommon {
  import DefaultJsonProtocol._

  implicit val jf_wal = jsonFormat3(Wallet)
  implicit val jf_wals = jsonFormat2(Wallets)
  
  implicit val jf_wal_cr = jsonFormat2(WalletCreateReq)
  implicit val jf_wal_rnd = jsonFormat1(WalletRandomReq)  
  implicit val jf_wal_res = jsonFormat2(WalletRes)    
  
  implicit val jf_wal_sig_req = jsonFormat9(WalletSignReq)
  implicit val jf_wal_tx_req = jsonFormat9(WalletTxReq)
  implicit val jf_wal_sig = jsonFormat2(WalletSig)  
  implicit val jf_wal_tx = jsonFormat2(WalletTx)  
  implicit val jf_wal_b_bal = jsonFormat3(BlockchainBalance)  
  implicit val jf_wal_bal = jsonFormat2(WalletBalance)  

  implicit val jf_ws_wal = jsonFormat8(WalletSecret)
}
