package io.syspulse.wal3

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.wal3.cypher.Cypher

case class WalletSecret(  
  sk:String,  
  pk:String,
  addr:String,

  oid:Option[String],
  
  typ:String = "ECDSA",
  ts:Long = System.currentTimeMillis(),

  cypher:String = "AES-256",
  metadata:String = ""          // arbitrary metadata (e.g. used by KMS for datakey)
)

// object WalletSecretEncrypted {
//   def from(wse:WalletSecretEncrypted)(implicit cypher:Cypher):Try[WalletSecret] = {
//     for {
//       sk <- cypher.decrypt(wse.sk, wse.metadata)
//       ws <- Success(WalletSecret(sk, wse.pk, wse.addr, wse.oid, wse.typ, wse.ts))
//     } yield (ws)  
//   }

//   def to(ws:WalletSecret)(implicit cypher:Cypher):Try[WalletSecretEncrypted] = {
//     for {
//       (sk,metadata) <- cypher.encrypt(ws.sk)
//       wse <- Success(WalletSecretEncrypted(sk, ws.pk, ws.addr, ws.oid, ws.typ, ws.ts, metadata = metadata))
//     } yield wse
//   }
// }
