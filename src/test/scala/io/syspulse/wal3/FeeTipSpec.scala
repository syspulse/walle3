package io.syspulse.wal3

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.{Try,Success,Failure}

class FeeTipSpec extends AnyWordSpec with Matchers {
  
  "FeeTip" should {

    "parse fee tip to ethereum" in {
      val t1 = FeeTips("ethereum = 0.1%")
      t1.get("ethereum") shouldBe Some(FeeTip("ethereum","0.1%"))
      t1.get("ethereum").get.tip shouldBe "0.1%"
      t1.get("ethereum").get.chain shouldBe "ethereum"
    }
    
    "parse multiple fee tips" in {
      val t1 = FeeTips("ethereum=0.1%,polygon=0.2%,arbitrum=0.3%")
      t1.get("ethereum") shouldBe Some(FeeTip("ethereum", "0.1%"))
      t1.get("polygon") shouldBe Some(FeeTip("polygon", "0.2%"))
      t1.get("arbitrum") shouldBe Some(FeeTip("arbitrum", "0.3%"))
    }

    "handle missing tips" in {
      val t1 = FeeTips("ethereum=0.1%")
      t1.get("polygon") shouldBe None
    }

    "handle default tips" in {
      val t1 = FeeTips("")
      t1.get("ethereum") shouldBe Some(FeeTip("ethereum","0.1%"))
    }

    "handle malformed tips" in {
      val t1 = FeeTips("ethereum=,polygon=0.2%")
      t1.get("ethereum") shouldBe Some(FeeTip("ethereum", "0.1%"))
      t1.get("polygon") shouldBe Some(FeeTip("polygon", "0.2%"))
    }

    "handle different tip formats" in {
      val t1 = FeeTips("ethereum=0.1%,polygon=1.5,arbitrum=$0.5,optimism=0.001ETH")
      t1.get("ethereum").get.tip shouldBe "0.1%"
      t1.get("polygon").get.tip shouldBe "1.5"
      t1.get("arbitrum").get.tip shouldBe "$0.5"
      t1.get("optimism").get.tip shouldBe "0.001ETH"
    }

    "handle whitespace in tips" in {
      val t1 = FeeTips(" ethereum = 0.1% , polygon = 0.2% ")
      t1.get("ethereum") shouldBe Some(FeeTip("ethereum", "0.1%"))
      t1.get("polygon") shouldBe Some(FeeTip("polygon", "0.2%"))
    }

    "handle case sensitivity" in {
      val t1 = FeeTips("ETHEREUM=0.1%,Polygon=0.2%")
      t1.get("ethereum") shouldBe Some(FeeTip("ethereum", "0.1%"))
      t1.get("polygon") shouldBe Some(FeeTip("Polygon", "0.2%"))
    }

    "handle duplicate chains" in {
      val t1 = FeeTips("ethereum=0.1%,ethereum=0.2%")
      t1.get("ethereum") shouldBe Some(FeeTip("ethereum", "0.2%")) // last one wins
    }
    
  }    
}
