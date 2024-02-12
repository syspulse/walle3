package io.syspulse.wal3

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import scala.util.Success

class KmsURISpec extends AnyWordSpec with Matchers {
  
  "KmsURI" should {
    
    "parse 'kms://arn:aws:kms:eu-west-1:12345678:key/1e7f33ec-a8e4-5583-b6c4-281af46c2e55'" in {
      val s = KmsURI("kms://arn:aws:kms:eu-west-1:12345678:key/1e7f33ec-a8e4-5583-b6c4-281af46c2e55")
      s.account should === (Some("12345678"))
      s.region should === (Some("eu-west-1"))
      s.key should === (Some("key/1e7f33ec-a8e4-5583-b6c4-281af46c2e55"))

      s.uri should === ("arn:aws:kms:eu-west-1:12345678:key/1e7f33ec-a8e4-5583-b6c4-281af46c2e55")
    }

    "parse 'kms://arn:aws:kms:eu-west-1:12345678'" in {
      val s = KmsURI("kms://arn:aws:kms:eu-west-1:12345678")
      s.account should === (Some("12345678"))
      s.region should === (Some("eu-west-1"))
    }

    "parse 'kms://http://localhost:4599'" in {
      val s = KmsURI("kms://http://localhost:4599")
      s.host should === (Some("http://localhost:4599"))
      s.account should === (None)
      s.region should === (None)
    }

    "parse 'kms://'" in {
      val s = KmsURI("kms://")
      s.host should === (None)
      s.account should === (sys.env.get("AWS_REGION"))
      s.region should === (sys.env.get("AWS_ACCOUNT"))
    }
  }

}
