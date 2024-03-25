package io.syspulse.wal3

import scala.util.{Try,Success,Failure}
import scala.jdk.CollectionConverters._
import io.syspulse.skel.Ingestable
import io.syspulse.skel.util.Util

case class Blockchain(
  name:String,
  id:Option[String] = None,  // chain_id 
  dec:Option[Int] = None,    // decimals
  tok:Option[String] = None
) {
  def asLong:Long = id.getOrElse("0").toLong
}

object Blockchain {
  type ID = String

  val ETHEREUM = Blockchain("ethereum",Some("1"),Some(18),Some("ETH"))
  val BSC_MAINNET = Blockchain("bsc",Some("56"),Some(8),Some("BNB"))
  val ARBITRUM_MAINNET = Blockchain("arbitrum",Some("42161"),Some(18),Some("ETH"))
  val OPTIMISM_MAINNET = Blockchain("optimism",Some("10"),Some(18),Some("ETH"))
  val POLYGON_MAINNET = Blockchain("polygon",Some("137"),Some(18),Some("MATIC"))

  val SCROLL_MAINNET = Blockchain("scroll",Some("534352"),Some(18),Some("ETH"))
  val ZKSYNC_MAINNET = Blockchain("zksync",Some("324"),Some(18),Some("ETH"))

  val LINEA_MAINNET = Blockchain("linea",Some("59144"),Some(18),Some("ETH"))
  val BASE_MAINNET = Blockchain("base",Some("8453"),Some(18),Some("ETH"))
  
  val SEPOLIA = Blockchain("sepolia",Some("11155111"),Some(18),Some("ETH"))
  val ANVIL = Blockchain("anvil",Some("31337"),Some(18),Some("ETH"))

  // default EVM
  val EVM = Blockchain("evm",Some("31337"),Some(18),Some("ETH"))

  val ALL = Seq(
    ETHEREUM,
    BSC_MAINNET,
    ARBITRUM_MAINNET,
    OPTIMISM_MAINNET,
    POLYGON_MAINNET,
    SCROLL_MAINNET,
    ZKSYNC_MAINNET,

    SEPOLIA,
    ANVIL,
    EVM
  )

  def resolve(name:String):Option[Blockchain] = ALL.find(b => b.name == name.trim)
  def resolveById(id0:String):Option[Blockchain] = {
    val id = Some(id0)
    ALL.find(b => b.id == id)  
  }
  
  def resolveChainId(chain:Blockchain):Option[Long] = chain.id match {
    case None => resolve(chain.name).map(b => b.id.get.toLong)
    case _ => Some(chain.id.get.toLong)
  }

  def resolve(chain:Blockchain):Try[Long] = Util.succeed(resolveChainId(chain)) match {
    case Success(chain) => Success(chain.get)
    case _ => Failure(new Exception(s"unknown chain: ${chain}"))
  }

  def resolve(chain:Option[Blockchain]):Try[Long] = chain match {
    case None => Failure(new Exception(s"unknown chain: ${chain}"))
    case Some(c) => resolve(c)
  }
}