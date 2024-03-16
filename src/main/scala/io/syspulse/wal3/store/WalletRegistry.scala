package io.syspulse.wal3.store

import scala.util.{Try,Success,Failure}

import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import io.syspulse.skel.Command

import io.syspulse.skel.crypto.Eth

import io.syspulse.wal3._
import io.syspulse.wal3.server._
import io.syspulse.wal3.signer.WalletSigner
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import io.syspulse.skel.util.Util

object WalletRegistry {
  val log = Logger(s"${this}")
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    
  final case class GetWallets(oid:Option[String],replyTo: ActorRef[Wallets]) extends Command
  final case class GetWallet(addr:String,oid:Option[String], replyTo: ActorRef[Try[Wallet]]) extends Command
  
  final case class CreateWallet(oid:Option[String], req: WalletCreateReq, replyTo: ActorRef[Try[Wallet]]) extends Command
  final case class RandomWallet(oid:Option[String], req: WalletRandomReq, replyTo: ActorRef[Try[Wallet]]) extends Command

  final case class DeleteWallet(addr: String, oid:Option[String], replyTo: ActorRef[Try[Wallet]]) extends Command

  final case class SignWallet(addr: String, oid:Option[String], req: WalletSignReq, replyTo: ActorRef[Try[WalletSig]]) extends Command
  final case class TxWallet(addr: String, oid:Option[String], req: WalletTxReq, replyTo: ActorRef[Try[WalletTx]]) extends Command
  final case class BalanceWallet(addr: String, oid:Option[String], req: WalletBalanceReq, replyTo: ActorRef[Try[WalletBalance]]) extends Command
  final case class TxStatusAsk(txHash: String, oid:Option[String], req: TxStatusReq, replyTo: ActorRef[Try[TxStatus]]) extends Command
  
  def apply(store: WalletStore,signer:WalletSigner,blockchains:Blockchains)(implicit config:Config): Behavior[io.syspulse.skel.Command] = {
    registry(store,signer,blockchains)(config)
  }

  private def registry(store: WalletStore,signer:WalletSigner,blockchains:Blockchains)(config:Config): Behavior[io.syspulse.skel.Command] = {    
        
    // Rules of oid
    // 1. if oid == None - this is access from admin/service account
    // 2. if oid == Some - this is access from user and needs to be validated
    Behaviors.receiveMessage {
      case GetWallets(oid, replyTo) =>
        val all = store.all(oid)
        val owned = if(oid==None) all else all.filter(ws => ws.oid == oid)
        replyTo ! Wallets(
          owned.map(ws => {
            log.debug(s"?: ${ws}")
            Wallet(ws.addr,ws.typ,ws.ts,ws.oid)
          }),
          total = Some(all.size)
        )
        Behaviors.same

      case GetWallet(addr0, oid, replyTo) =>
        val addr = addr0.toLowerCase()

        val ws = for {
          ws0 <- store.???(addr, oid)
          b <- if(oid == None) Success(true) else Success(ws0.oid == oid)
          ws1 <- if(b) Success(ws0) else Failure(new Exception(s"not found: ${addr}"))
        } yield ws1
        
        ws match {
          case Success(ws) =>
            log.info(s"?: ${ws}")
            replyTo ! Success(Wallet(ws.addr,ws.typ,ws.ts,ws.oid))
          case Failure(e)=> 
            log.error(s"failed to get wallet: ${oid},${addr}",e)
            replyTo ! Failure(e)
        }
        
        Behaviors.same

      case CreateWallet(oid, req, replyTo) =>
        val w = for {
          ws <- signer.create(oid,req.sk)
          _ <- store.+++(ws)
          w <- {
            log.info(s"add: ${ws}")

            Success(Wallet(ws.addr, ws.typ,ws.ts,ws.oid))
          }
        } yield w        
        
        w match {
          case Success(_) =>            
            replyTo ! w
          case Failure(e)=> 
            log.error(s"failed to create wallet: ${oid},${req}",e)
            replyTo ! Failure(e)
        }

        Behaviors.same
      
      case RandomWallet(oid, req, replyTo) =>
        val w = for {
          ws <- signer.random(oid)
          _ <- store.+++(ws)
          w <- {
            log.info(s"add: ${ws}")
            Success(Wallet(ws.addr, ws.typ,ws.ts,ws.oid))
          }
        } yield w        
        
        w match {
          case Success(_) =>            
            replyTo ! w
          case Failure(e)=> 
            log.error(s"failed to create wallet: ${oid},${req}",e)
            replyTo ! Failure(e)
        }

        Behaviors.same
      
      case DeleteWallet(addr0, oid, replyTo) =>
        val addr = addr0.toLowerCase()

        val ws = store.del(addr,oid)        
        ws match {
          case Success(ws) => 
            log.info(s"del: ${ws}")
            replyTo ! Success(Wallet(ws.addr, ws.typ, ws.ts,ws.oid))
          case Failure(e) => 
            log.error(s"failed to delete wallet: ${oid},${addr}",e)
            replyTo ! Failure(e)
        }
        
        Behaviors.same

      case SignWallet(addr0, oid, req, replyTo) =>        
        val addr = addr0.toLowerCase()

        val sig:Try[String] = for {
          chainId <- Success(req.chain.getOrElse(Blockchain.ANVIL).asLong)
          ws0 <- store.???(addr,oid)

          web3 <- blockchains.getWeb3(chainId)
          nonceTx <- if(req.nonce == -1L) Eth.getNonce(addr)(web3) else Success(req.nonce)
          gasPrice <- if(req.gasPrice.isBlank()) Eth.getGasPrice()(web3) else Eth.strToWei(req.gasPrice)(web3)
          gasTip <- if(req.gasTip.isBlank()) Success(BigInt(0)) else Eth.strToWei(req.gasTip)(web3)
          value <- Eth.strToWei(req.value.getOrElse("0"))(web3)
          
          b <- if(oid == None) Success(true) else Success(ws0.oid == oid)
          ws1 <- if(b) Success(ws0) else Failure(new Exception(s"not found: ${addr}"))          
          
          sig <- {
            log.info(s"sign: ${ws1}: ${req}")
            // signing by admin on behalf of another address/wallet is possible
            signer.sign(ws1, req.to, nonceTx, req.data, gasPrice, gasTip, req.gasLimit, value, chainId)
          }
        } yield sig
        
        sig match {
          case Success(sig) =>            
            replyTo ! Success(WalletSig(addr,sig))
          case Failure(e)=> 
            log.error(s"failed to sign transaction: ${oid},${addr},${req}",e)
            replyTo ! Failure(e)
        }

        Behaviors.same

      case TxWallet(addr0, oid, req, replyTo) =>        
        val addr = addr0.toLowerCase()

        val txHash:Try[String] = for {
          chainId <- Success(req.chain.getOrElse(Blockchain.ANVIL).asLong)
          ws0 <- store.???(addr,oid)
          
          web3 <- blockchains.getWeb3(chainId)
          nonceTx <- if(req.nonce == -1L) Eth.getNonce(addr)(web3) else Success(req.nonce)
          gasPrice <- if(req.gasPrice.isBlank()) Eth.getGasPrice()(web3) else Eth.strToWei(req.gasPrice)(web3)
          gasTip <- if(req.gasTip.isBlank()) Success(BigInt(0)) else Eth.strToWei(req.gasTip)(web3)
          value <- Eth.strToWei(req.value.getOrElse("0"))(web3)
          
          b <- if(oid == None) Success(true) else Success(ws0.oid == oid)
          ws1 <- if(b) Success(ws0) else Failure(new Exception(s"not found: ${addr}"))          
          
          sig <- {
            log.info(s"sign: ${ws1}: ${req}")
            // signing by admin on behalf of another address/wallet is possible
            signer.sign(ws1, req.to, nonceTx, req.data, gasPrice, gasTip, req.gasLimit, value, chainId)
          }

          hash <- {
            log.info(s"transaction: ${ws1}: ${sig}")            
            Eth.send(sig)(web3)
          }
        } yield hash
        
        txHash match {
          case Success(hash) =>            
            replyTo ! Success(WalletTx(addr,hash))
          case Failure(e)=> 
            log.error(s"failed to send transaction: ${oid},${addr},${req}",e)
            replyTo ! Failure(e)
        }

        Behaviors.same

      case BalanceWallet(addr0, oid, req, replyTo) => 
        val addr = addr0.toLowerCase()

        def getBalance(addr:String, oid:Option[String], req:WalletBalanceReq, replyTo: ActorRef[Try[WalletBalance]]) = {
          val balances:Try[Seq[BlockchainBalance]] = for {
            // ws0 <- store.???(addr,oid)
            // b <- if(oid == None) Success(true) else Success(ws0.oid == oid)
            // ws1 <- if(b) Success(ws0) else Failure(new Exception(s"not found: ${addr}"))
            
            web3s <- {
              val bb:Seq[BlockchainRpc] = if(req.chains.size == 0) 
                blockchains.all()
              else 
                req.chains.flatMap(b => {                  
                  if(b.id.isDefined)
                    blockchains.get(b.id.get.toLong)
                  else
                    blockchains.getByName(b.name)
                })
              
              val web3s = bb.flatMap(b => {
                blockchains.getWeb3(b.id).toOption match {
                  case Some(web3) => Some((b,web3))
                  case _ => 
                    log.warn(s"could not get Web3: ${addr}: ${b}")
                    None
                }
              })            
              Success(web3s)
            }
          
            balances <- {
              Success(
                web3s.flatMap(web3 => {
                  log.info(s"balance: ${addr}: ${web3}")
                  Eth.getBalance(addr)(web3._2).toOption match {
                    case Some(bal) => Some(BlockchainBalance(web3._1.name,web3._1.id,bal))
                    case _ => 
                      log.warn(s"could not get Balance: ${addr}: ${web3}")
                      None
                  }
                })
              )
            }
            
          } yield balances
          
          balances match {
            case Success(balances) =>            
              replyTo ! Success(WalletBalance(addr,balances))
            case Failure(e)=> 
              log.error(s"failed to get balances: ${oid},${addr},${req}",e)
              replyTo ! Failure(e)
          }
        }

        def getBalanceAsync(addr:String, oid:Option[String], req:WalletBalanceReq, replyTo: ActorRef[Try[WalletBalance]]) = {
          
          val balances = for {
            web3s <- {
              val bb:Seq[BlockchainRpc] = if(req.chains.size == 0) 
                blockchains.all()
              else 
                req.chains.flatMap(b => {
                  if(b.id.isDefined)
                    blockchains.get(b.id.get.toLong)
                  else
                    blockchains.getByName(b.name)
                })
              
              val web3s = bb.flatMap(b => {
                blockchains.getWeb3(b.id).toOption match {
                  case Some(web3) => Some((b,web3))
                  case _ => 
                    log.warn(s"could not get Web3: ${addr}: ${b}")
                    None
                }
              })            
              Success(web3s)
            }
          
            ff <- {
              val ff = web3s.map(web3 => {
                log.info(s"balance: ${addr}: ${web3}")
                // ask for Future wrapped into try
                val f = Eth.getBalanceAsync(addr)(web3._2,ec)
                f                
              })
              Success(ff)
            }

            balances <- {
              
              val f = Util.waitAll(ff)
              try {
                val bals = Await.result(f,FiniteDuration(config.rpcTimeout,TimeUnit.MILLISECONDS))
                
                // Can iterate over web3s because all future must return successfully here            
                Success(
                  web3s.zip(bals).map{ case(web3,bal) =>
                    bal match {
                      case Success(bal) => BlockchainBalance(web3._1.name,web3._1.id,bal)
                      case Failure(e) => 
                        log.warn(s"failed to get balance: ${oid},${addr},${web3}",e)
                        BlockchainBalance(web3._1.name,web3._1.id,-1)
                    }
                  }
                )
                
              } catch {
                case e:Exception => 
                  log.error(s"failed to get balances: ${oid},${addr},${req}",e)
                  Failure(e)
              }
            }
            
          } yield balances
          
          balances match {
            case Success(balances) =>            
              replyTo ! Success(WalletBalance(addr,balances))
            case Failure(e)=> 
              log.error(s"failed to get balances: ${oid},${addr},${req}",e)
              replyTo ! Failure(e)
          }
        }

        log.info(s"asking balances: ${oid}: ${addr}: ${req.chains}")
        
        getBalanceAsync(addr,oid,req,replyTo)

        Behaviors.same

      case TxStatusAsk(txHash, oid:Option[String], req:TxStatusReq, replyTo) =>        
        
        val status:Try[String] = for {
          chain <- if(req.chain.isDefined) Success(req.chain.get) else Failure(new Exception(s"undefined chain"))
          chainId <- Blockchain.resolve(chain)
          web3 <- blockchains.getWeb3(chainId)          
                    
          status <- {
            log.info(s"transaction: ${txHash}")            
            val receipt = web3.ethGetTransactionReceipt(txHash).send().getTransactionReceipt()
            if(receipt.isPresent())
              Success(receipt.get().getStatus())
            else
              Failure(new Exception(s"tx not found: ${txHash}"))
          }
        } yield status
        
        status match {
          case Success(status) =>            
            replyTo ! Success(TxStatus(txHash,status))
          case Failure(e)=> 
            log.error(s"failed to get tx status: ${oid},${txHash},${req}",e)
            replyTo ! Failure(e)
        }

        Behaviors.same

    }
        
  }

}
