package io.syspulse.wal3

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import io.syspulse.skel.util.Util

import org.web3j.protocol.Web3j
import io.syspulse.skel.crypto.Eth

case class Blockchain(name:String,id:Long,rpcUri:String) 

class Blockchains(bb:Seq[String]) {

  override def toString():String = blockchains.toString

  protected var blockchains:Map[Long,Blockchain] = Map(
    // 1L -> Blockchain("mainnet",1L, "https://eth.drpc.org"),
    // 42161L -> Blockchain("arbitrum",42161L,"https://rpc.ankr.com/arbitrum"),
    // 10L -> Blockchain("optimism",10L,"https://optimism-mainnet.public.blastapi.io"),
    // 137L -> Blockchain("polygon",137L,"https://polygon.blockpi.network/v1/rpc/public"),
    // 56L -> Blockchain("bsc",56L,"https://rpc-bsc.48.club"),
    // 100L -> Blockchain("gnosis",100L,"https://rpc.gnosis.gateway.fm"),

    // 534352L -> Blockchain("scroll",534352L,"https://rpc.scroll.io"),
    // 324L -> Blockchain("zksync",324L,"https://mainnet.era.zksync.io"),

    31337L -> Blockchain("anvil",31337L,"http://localhost:8545"),
    11155111L -> Blockchain("sepolia",11155111L,"https://eth-sepolia.public.blastapi.io"),
  )

  def ++(bb:Seq[String]):Blockchains = {
    val newBlockchains = bb.flatMap(b =>{
      b.replaceAll("\n","").split("=").toList match {
        case id :: name :: rpc :: _ => 
          val bid = id.trim.toLong
          Some(( bid ->  Blockchain(name.trim(),bid,rpc), bid -> Eth.web3(rpc.trim()) ))
        case id :: rpc :: Nil => 
          val bid = id.trim.toLong
          Some(( bid ->  Blockchain(bid.toString,bid,rpc), bid -> Eth.web3(rpc.trim()) ))
        case rpc :: Nil => 
          if(rpc.isBlank())
            None
          else
            Some(( 1L ->  Blockchain("mainnet",1L,rpc), 1L -> Eth.web3(rpc) ))
        case _ => None
      }
    })
    blockchains = blockchains ++ newBlockchains.map(_._1).toMap
    rpc = rpc ++ newBlockchains.map(_._2).toMap
    this
  }

  // map of connections
  var rpc:Map[Long,Web3j] = blockchains.values.map( b => {
    b.id -> Eth.web3(b.rpcUri)
  }).toMap

  def get(id:Long) = blockchains.get(id)
  def getByName(name:String) = blockchains.values.find(_.name == name.toLowerCase())
  def getWeb3(id:Long) = rpc.get(id) match {
    case Some(web3) => Success(web3)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def all():Seq[Blockchain] = blockchains.values.toSeq

  // add default blockchains
  this.++(bb)
}

object Blockchains {
  def apply(bb:Seq[String]) = new Blockchains(bb)
  def apply(bb:String) = new Blockchains(bb.split(",").toSeq)
  def apply() = new Blockchains(Seq())
}