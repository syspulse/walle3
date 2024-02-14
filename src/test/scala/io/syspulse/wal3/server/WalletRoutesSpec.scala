package io.syspulse.wal3.server

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

class WalletRoutesSpec extends AnyWordSpec with Matchers {
  
  "WalletRoutes" should {
    val j0 = """
{
  "exp": 1710503139,
  "iat": 1707911139,
  "jti": "b1f9b191-711c-4005-ad79-d100ba73fee4",
  "iss": "",
  "sub": "026272c9-8b02-4aa2-ba92-89f90908788e",
  "typ": "Bearer",
  "azp": "system",
  "upn": "service-account",
  "clientHost": "109.108.74.188",
  "groups": [
    "default-roles-api",
    "user",
    "service-role"
  ],
  "client_id": "account-1"
}
      """

    "parse JWT service role" in {      
      val r = WalletRoutes.parseJson(j0,"groups[].service-role")
      r.get.contains("service-role") should ===(true)      
    }

    "parse objects tree" in {
      val r = WalletRoutes.parseJson("""{"data": {"role": "service"} }""","data.role.service")
      info(s"r = ${r}")
      r.get should ===(Seq("service"))
    }

    "parse objects tree and not find" in {
      val r = WalletRoutes.parseJson("""{"data": {"role": "service"} }""","data.role.service_2")
      info(s"r = ${r}")
      r.get should ===(Seq())
    }

    "parse objects tree with array items" in {      
      val j2 = """{"data": {"groups": ["user","service"]} }"""
      WalletRoutes.parseJson(j2,"data.groups[].service").get should === (Seq("service"))
      WalletRoutes.parseJson(j2,"data.groups[].user").get should === (Seq("user"))
      WalletRoutes.parseJson(j2,"data.groups[].UNKNWON").get should === (Seq())            
    }
    
    "parse objects tree with array" in {      
      val j2 = """{"data": {"groups": ["user","service"]} }"""
      WalletRoutes.parseJson(j2,"data.groups[]").get should === (Seq("user","service"))      
    }
    
  }    
}
