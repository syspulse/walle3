package io.syspulse.wal3.signer

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import io.jvm.uuid._

import scala.util.{Try,Success,Failure}
import java.time._
import io.syspulse.wal3.WalletSecret
import io.syspulse.skel.crypto.Eth
import scala.util.Random
import io.syspulse.skel.util.Util
import io.syspulse.wal3.cypher.CypherNone
import io.syspulse.blockchain.Blockchains
import io.syspulse.wal3.store.WalletStoreSSS
import io.syspulse.skel.crypto.SSS
import io.syspulse.wal3.store.WalletRegistry.SignWallet
import io.syspulse.wal3.server.WalletSignReq
import spray.json.JsString
import spray.json.JsArray
import spray.json.JsObject
// import io.syspulse.skel.util.Util

class SignerSpec extends AnyWordSpec with Matchers {
  
  "SSS Signer" should {

    "SecretShare encode and decode 1 Share" in {
      val s1 = SecretShare(
        "1/" +
        "7361427092681313269810524519830455803016610949053270811455406004054541098502340806596485551435667501112158150867880594089355108900763350474560900214074349/" +
        "0xbeb50a33de0d8234f0efd51c90ef339e46089001e6afa62b9f79e8158588f9ae/" +
        "7361427092681313269810524519830455803016610949053270811455406004054541098502376421162898176865507507308336261795884733274980097792286908726033065279199919")

      s1 shouldBe a [List[_]]
      s1.size should === (1)
      s1(0).x should === (BigInt(1))
      s1(0).y should === (BigInt("7361427092681313269810524519830455803016610949053270811455406004054541098502340806596485551435667501112158150867880594089355108900763350474560900214074349"))
      s1(0).hash should === (Util.fromHexString("0xbeb50a33de0d8234f0efd51c90ef339e46089001e6afa62b9f79e8158588f9ae"))
      s1(0).primeUsed should === ("7361427092681313269810524519830455803016610949053270811455406004054541098502376421162898176865507507308336261795884733274980097792286908726033065279199919")
    }

    "SecretShare encode and decode 3 Shares" in {
      val s1 = SecretShare(
        "1/" +
        "7361427092681313269810524519830455803016610949053270811455406004054541098502340806596485551435667501112158150867880594089355108900763350474560900214074349/" +
        "0xbeb50a33de0d8234f0efd51c90ef339e46089001e6afa62b9f79e8158588f9ae/" +
        "7361427092681313269810524519830455803016610949053270811455406004054541098502376421162898176865507507308336261795884733274980097792286908726033065279199919" +
        ","+
        "2/" +
        "7361427092681313269810524519830455803016610949053270811455406004054541098502340806596485551435667501112158150867880594089355108900763350474560900214074349/" +
        "0xbeb50a33de0d8234f0efd51c90ef339e46089001e6afa62b9f79e8158588f9ae/" +
        "7361427092681313269810524519830455803016610949053270811455406004054541098502376421162898176865507507308336261795884733274980097792286908726033065279199919" +
        "," +
        "3/" +
        "7361427092681313269810524519830455803016610949053270811455406004054541098502340806596485551435667501112158150867880594089355108900763350474560900214074349/" +
        "0xbeb50a33de0d8234f0efd51c90ef339e46089001e6afa62b9f79e8158588f9ae/" +
        "7361427092681313269810524519830455803016610949053270811455406004054541098502376421162898176865507507308336261795884733274980097792286908726033065279199919"
      )

      s1 shouldBe a [List[_]]
      s1.size should === (3)
      s1(0).x should === (BigInt(1))
      s1(0).y should === (BigInt("7361427092681313269810524519830455803016610949053270811455406004054541098502340806596485551435667501112158150867880594089355108900763350474560900214074349"))
      s1(0).hash should === (Util.fromHexString("0xbeb50a33de0d8234f0efd51c90ef339e46089001e6afa62b9f79e8158588f9ae"))
      s1(0).primeUsed should === ("7361427092681313269810524519830455803016610949053270811455406004054541098502376421162898176865507507308336261795884733274980097792286908726033065279199919")

      s1(1).x should === (BigInt(2))
      s1(2).x should === (BigInt(3))
    }

    "create from 0x1 and sign" in {
      val s = new WalletSignerSSS(new CypherNone(""),"1:2",Blockchains())
      val ss = s.create(None,"0x1")
      
      ss.isSuccess should === (true)
      val ws = ss.get.ws
      ws.metadata should === (",")
      ws.metadata.split(",",1024).size should === (2)
      ws.sk.split(",",1024).size should === (2)

      val sig1 = s.sign(SignerSecret(ws),SignerTxPayload("0x2",0,"",0,0,0,0,0))
      info(s"sig1=${sig1}")
      sig1.isSuccess should === (true)
      sig1.get should !== ("")
    }

    // "SecretShare not working" in {
    //   val sk1 = 
    //     "1/7361427092681313269810524519830455803016610949053270811455406004054541098502369971808103681267527383901986615384795076880776173650919361020246422177165906/0x5904430603cbbb583132783aedd6a491fc856ecfc346005d5fb29efd91f7a876/7361427092681313269810524519830455803016610949053270811455406004054541098502376421162898176865507507308336261795884733274980097792286908726033065279199919, " +
    //     "2/7361427092681313269810524519830455803016610949053270811455406004054541098502369971808103681267527383901986615384795076880776173650919361020246422177165906/0x5904430603cbbb583132783aedd6a491fc856ecfc346005d5fb29efd91f7a876/7361427092681313269810524519830455803016610949053270811455406004054541098502376421162898176865507507308336261795884733274980097792286908726033065279199919"      
    //   val sk2 = 
    //     "1/24503835328911757279057362924498432051587610320127844972182541025947753701044/0xa9c54a7ae69848ad803c8e042c3c5d2c9077647ad03d8d8d3e6d79e9b66eea47/7361427092681313269810524519830455803016610949053270811455406004054541098502376421162898176865507507308336261795884733274980097792286908726033065279199919, " +
    //     "2/24503835328911757279057362924498432051587610320127844972182541025947753701044/0xa9c54a7ae69848ad803c8e042c3c5d2c9077647ad03d8d8d3e6d79e9b66eea47/7361427092681313269810524519830455803016610949053270811455406004054541098502376421162898176865507507308336261795884733274980097792286908726033065279199919"


    //   val ss1 = SecretShare.fromList(sk1).map(SecretShare.toShare(_).get)
    //   ss1(0).x should !== (ss1(1).x)
    //   ss1(0).y should === (ss1(1).y)
    //   ss1(0).hash should === (ss1(1).hash)
    //   ss1(0).primeUsed should === (ss1(1).primeUsed)

    //   val ss2 = SecretShare.fromList(sk2).map(SecretShare.toShare(_).get)
    //   ss2(0).x should !== (ss2(1).x)
    //   ss2(0).y should === (ss2(1).y)
    //   ss2(0).hash should === (ss2(1).hash)
    //   ss2(0).primeUsed should === (ss2(1).primeUsed)
      
    //   val signer = new WalletSignerSSS(new CypherNone(""),"1:2",Blockchains())
    //   val store = new WalletStoreSSS(1)

    //   val w1 = WalletSecret(sk1,"","0x001",None,"ECDSA",0,"","")
    //   val w2 = WalletSecret(sk2,"","0x002",None,"ECDSA",0,"","")
    //   val ww1 = store.+++(w1)
    //   val ww2 = store.+++(w2)


    //   val www1 = store.???(ww1.get.addr,None)
    //   www1.isSuccess should === (true)      
    //   val sig1 = signer.sign(www1.get,"0xffffffffffffffffff",0,"",0,0,0)
    //   info(s"sig1=${sig1}")
    //   sig1.isSuccess should === (true)
    //   sig1.get should !== ("")

    //   // val www2 = store.???(ww2.get.addr,None)
    //   // www2.isSuccess should === (true)      
    //   // val sig2 = signer.sign(www2.get,"0xffffffffffffffffff",0,"",0,0,0)
    //   // info(s"sig2=${sig2}")
    //   // sig2.isSuccess should === (true)
    //   // sig2.get should !== ("")
      
    // }

    "Share serialization (FAILS on non String secret !)" in {
      for(i <- 0 until 100) {
        val secret = Util.hex(Random.nextBytes(64))
        //val secret = new String(Random.nextBytes(32))
        val sh1 = SSS.createShares(secret,1,2)
        new String(SSS.getSecret(sh1.get).get) should === (secret)
        
        val ss1 = SecretShare.fromShares(sh1.get)
        ss1.size should === (2)
        val sh2 = SecretShare.toShares(ss1)
        //info(s"sh2=${sh2}")
        new String(SSS.getSecret(sh2).get) should === (secret)
      }
    }

    "Sign with SSS" in {      
      val signer = new WalletSignerSSS(new CypherNone(""),"1:2",Blockchains())
      val store = new WalletStoreSSS("sss://1")

      for(i <- 0 until 100) {
        val s1 = signer.random(None).get
        val ss1 = store.+++(s1)
        val www1 = store.???(ss1.get.ws.addr,None)
        www1.isSuccess should === (true)      
        val sig1 = signer.sign(SignerSecret(www1.get),SignerTxPayload("0xffffffffffffffffff",0,"",0,0,0,0,0))
        sig1.isSuccess should === (true)
        sig1.get should !== ("")
      }
    }

    "Sign with SSS as Request" in {      
      val signer = new WalletSignerSSS(new CypherNone(""),"1:2",Blockchains())
      val store = new WalletStoreSSS("sss://1")

      val a1 = SignWallet(
        "0x001",
        None,
        WalletSignReq(None,"0x0001",0L,"","0gwei","0gwei",20000L,None,
          signerType = Some("sss"),
          signerData = Some(JsObject("shares" -> JsArray(
            JsString("2/200/0xfff1/0x1"),
          )))
        ),
        null
      )
      val r1 = a1.req

      val w1 = signer.random(None).get
      val sd1 = signer.decodeSignerData(r1.signerType,r1.signerData)
      sd1 shouldBe a [Option[SignerSSSUserShare]]
      
      
    }    
  }
}
