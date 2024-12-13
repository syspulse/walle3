package io.syspulse.wal3

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import io.syspulse.skel.util.Util

import org.web3j.protocol.Web3j
import io.syspulse.skel.crypto.Eth
import io.syspulse.blockchain.Blockchain

case class FeeTip(chain:String,tip:String) 

class FeeTips(tt:Seq[String]) {

  override def toString():String = tips.toString

  protected var tips:Map[String,FeeTip] = Map(
    Blockchain.ETHEREUM.name -> FeeTip(Blockchain.ETHEREUM.name,"0.1%"),
    Blockchain.ANVIL.name -> FeeTip(Blockchain.ANVIL.name,"10%"),
  )

  def ++(bb:Seq[String]):FeeTips = {
    val newFeeTips = bb.flatMap(b =>{
      b.replaceAll("\n","").split("=").toList match {
        case chain :: tip :: _ => 
          Some(( chain.trim ->  FeeTip(chain.trim,tip.trim)))
        case _ => None
      }
    })
    tips = tips ++ newFeeTips
    this
  }
  
  def get(chain:String) = tips.values.find(_.chain.toLowerCase() == chain.toLowerCase())  
  def all():Seq[FeeTip] = tips.values.toSeq

  // add default tips
  this.++(tt)

  def get(chain:Option[Blockchain]):String = chain match {
    case Some(chain) => get(chain.name).map(_.tip).getOrElse("0")
    case None => "0"
  }
}

object FeeTips {
  def apply(tips:Seq[String]) = new FeeTips(tips)
  def apply(tipConfig:String) = new FeeTips(tipConfig.split(",").filter(_.nonEmpty).toSeq)
  def apply() = new FeeTips(Seq())
}