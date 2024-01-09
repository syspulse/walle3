package io.syspulse.wal3

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

class BlockchainSpec extends AnyWordSpec with Matchers {
  
  "BlockchainSpec" should {

    "parse multiline config" in {
      val bb = Blockchains("""
      1=eth=https://eth.drpc.org,
    42161=arb=https://rpc.ankr.com/arbitrum,
      """)

      info(s"${bb.all()}")
      
      bb.all().size should ===(4)
      bb.get(1L) should !==(None)
      bb.get(42161L) should !==(None)
      
    }
    
  }    
}
