package io.syspulse.wal3.store

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
// import io.syspulse.skel.util.Util

class WalletStoreKMSSpec extends AnyWordSpec with Matchers {
  
  "WalletStoreKMS" should {

    // "create and delete KMS key" in {
    //   val s = new WalletStoreKMS()
    //   val oid = UUID.random
    //   val w0 = WalletSecret("","","",Some(oid))
    //   val w1 = s.+++(w0)

    //   w1 shouldBe a [Success[_]]      
      
    //   val s2 = s.del(w1.get.addr)
    //   //info(s"w2 = ${s2}")

    //   s2 shouldBe a [Success[_]]
      
    //   val w2 = s.?(w1.get.addr)

    //   w2 shouldBe a [Failure[_]]
    // }

    // "create and find KMS key" in {
    //   val s = new WalletStoreKMS()
    //   val oid = UUID.random //UUID("93bf5f75-d412-483b-b51d-2073dd81635c")
    //   //val a0 = Util.hex(Random.nextBytes(20)) //"0x388C818CA8B9251b393131C08a736A67ccB19297"
    //   val w0 = WalletSecret("","","",Some(oid))
    //   val w1 = s.+++(w0)

    //   w1 shouldBe a [Success[_]]      

    //   // info(s"w1 = ${w1}")

    //   val w2 = s.?(w1.get.addr)
    //   //info(s"w2 = ${w2}")

    //   w2 shouldBe a [Success[_]]
    //   w1.get.addr should === (w2.get.addr)
    //   w1.get.pk should === (w2.get.pk)
    //   w1.get.oid should === (w2.get.oid)

    //   w1.get.metadata should === (w2.get.metadata)
    //   w2.get.cypher should === ("KMS")
    // }

    // "not find unknown KMS key" in {
    //   val s = new WalletStoreKMS()
    //   val oid = UUID.random //UUID("93bf5f75-d412-483b-b51d-2073dd81635c")
      
    //   val w2 = s.?("0x388C818CA8B9251b393131C08a736A67ccB19297")
    //   //info(s"w2 = ${w2}")

    //   w2 shouldBe a [Failure[_]]      
    // }

    // "create and find KMS key only for its oid" in {
    //   val s = new WalletStoreKMS()
    //   val oid = UUID.random      
    //   val w0 = WalletSecret("","","",Some(oid))
    //   val w1 = s.+++(w0)

    //   w1 shouldBe a [Success[_]]      

    //   val w2 = s.???(w1.get.addr,Some(oid))
    //   //info(s"w2 = ${w2}")
    //   w2 shouldBe a [Success[_]]
      
    //   val w3 = s.???(w1.get.addr,Some(UUID.random))
    //   w3 shouldBe a [Failure[_]]    
    // }

    // "list KMS keys" in {
    //   val s = new WalletStoreKMS()
    //   val w1 = s.all(None)
      
    //   w1.size should !== (0)
    // }

    // "list KMS keys only for own oid" in {
    //   val s = new WalletStoreKMS()
      
    //   val oid = UUID.random //UUID("93bf5f75-d412-483b-b51d-2073dd81635c")
    //   s.+++(WalletSecret("","","",Some(oid)))
    //   s.+++(WalletSecret("","","",Some(oid)))

    //   val w1 = s.all(Some(oid))

    //   //info(s"w1 = ${w1}")

    //   w1.size should === (2)
    // }

    // "not list KMS keys for unknown oid" in {
    //   val s = new WalletStoreKMS()
      
    //   val oid = UUID("93bf5f75-d412-483b-b51d-2073dd81635c")      
    //   val w1 = s.all(Some(oid))
      
    //   w1.size should === (0)
    // }

    "sign with KMS key" in {
      val s = new WalletStoreKMS()
      val oid = UUID.random
      val w0 = WalletSecret("","","",Some(oid))
      val w1 = s.+++(w0)

      w1 shouldBe a [Success[_]]
      
      for( i <- 0 to 5) {
                
        val sig1 = s.sign(w1.get,"0x1",0,"",BigInt(20),BigInt(1),20000,BigInt(0),chainId=11155111)
        info(s"sig1 = ${sig1}")

        sig1 shouldBe a [Success[_]]
      }
            
    }
  }    
}
