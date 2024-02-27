package io.syspulse.wal3.cypher

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

class CypherSpec extends AnyWordSpec with Matchers {
  
  "CypherKey" should {

    "encrypt and decrypt data with key" in {
      val c1 = new CypherKey("key-1")

      val e1 = c1.encrypt("data-1")
      e1 shouldBe a [Success[_]]
      e1.get._1.size should !== (0)
      e1.get._2.size should !== (0)
      
      val d1 = c1.decrypt(e1.get._1,e1.get._2)
      d1 shouldBe a [Success[_]]
      d1.get should === ("data-1")      
    }

    "encrypt and decrypt same data with key and Data/IV should be different" in {
      val c1 = new CypherKey("key-1")

      val e1 = c1.encrypt("data-1")
      e1 shouldBe a [Success[_]]
      e1.get._1.size should !== (0)
      e1.get._2.size should !== (0)

      val e2 = c1.encrypt("data-1")
      e2 shouldBe a [Success[_]]
      e2.get._1.size should !== (0)
      e2.get._2.size should !== (0)

      e1.get._1 should !== (e2.get._1)    
      e1.get._2 should !== (e2.get._2)

      val d1 = c1.decrypt(e1.get._1,e1.get._2)
      d1 shouldBe a [Success[_]]      
      val d2 = c1.decrypt(e1.get._1,e1.get._2)
      d2 shouldBe a [Success[_]]
      
      d1.get should === (d2.get)
    }
    
  }    
}
