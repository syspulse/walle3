package io.syspulse.wal3.store

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import io.jvm.uuid._

import scala.util.{Try,Success,Failure}
import java.time._
import io.syspulse.wal3.WalletSecret
// import io.syspulse.skel.util.Util

class WalletStoreKMSSpec extends AnyWordSpec with Matchers {
  
  "WalletStoreKMS" should {

    "create and find KMS key" in {
      val s = new WalletStoreKMS()
      val oid = UUID("93bf5f75-d412-483b-b51d-2073dd81635c")
      val a0 = "0x388C818CA8B9251b393131C08a736A67ccB19297"
      val w0 = WalletSecret("","",a0,Some(oid))
      val w1 = s.+++(w0)

      info(s"w1 = ${w1}")

      val w2 = s.?(a0)
      info(s"w2 = ${w2}")
    }

  }    
}
