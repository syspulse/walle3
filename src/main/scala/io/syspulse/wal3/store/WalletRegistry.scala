package io.syspulse.wal3.store

import scala.util.{Try,Success,Failure}

import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Await
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem

import io.syspulse.skel.Command

import io.syspulse.skel.crypto.Eth
import io.syspulse.skel.util.Util
import io.syspulse.skel.blockchain.{ Blockchains,Blockchain,BlockchainRpc }

import io.syspulse.wal3._
import io.syspulse.wal3.server._
import io.syspulse.wal3.signer.WalletSigner
import io.syspulse.wal3.signer.SignerSecret
import io.syspulse.wal3.signer.SignerTxPayload

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
  final case class TxCostAsk(addr: String, oid:Option[String], req: TxCostReq, replyTo: ActorRef[Try[TxCost]]) extends Command
  final case class GasPriceAsk(req: BlockchainReq, replyTo: ActorRef[Try[GasPrice]]) extends Command
  final case class CallWallet(addr: String, oid:Option[String], req: WalletCallReq, replyTo: ActorRef[Try[WalletCall]]) extends Command
  final case class SignWallet712(addr: String, oid:Option[String], req: WalletSign712Req, replyTo: ActorRef[Try[WalletSig]]) extends Command
  
  def apply(store: WalletStore,signer:WalletSigner,blockchains:Blockchains,tips:FeeTips)(implicit config:Config): Behavior[io.syspulse.skel.Command] = {
    //registry(store,signer,blockchains)(config)
    Behaviors.setup { context =>
      registry(store, signer, blockchains, tips, context.system)(config)
    }
  }

  private def registry(
    store: WalletStore,
    signer:WalletSigner,
    blockchains:Blockchains,
    tips:FeeTips,
    system: ActorSystem[_])(config:Config): Behavior[io.syspulse.skel.Command] = {
      
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
            log.debug(s"?: ${ws}")
            replyTo ! Success(Wallet(ws.addr,ws.typ,ws.ts,ws.oid))
          case Failure(e)=> 
            log.warn(s"failed to get wallet: ${oid},${addr}: ${e.getMessage}",e)
            replyTo ! Failure(e)
        }
        
        Behaviors.same
      
      case CreateWallet(oid, req, replyTo) =>
        val w = for {
          ss0 <- signer.create(oid,req.sk)
          ss <- store.+++(ss0)
          w <- {
            val ws = ss.ws
            log.info(s"add: ${ss}")

            val signerData = signer.encodeSignerData(ss)
            Success(Wallet(ws.addr, ws.typ,ws.ts,ws.oid,signerData))
          }
        } yield w        
        
        w match {
          case Success(_) =>            
            replyTo ! w
          case Failure(e)=> 
            log.error(s"failed to create wallet: ${oid},${req}: ${e.getMessage()}",e)
            replyTo ! Failure(e)
        }

        Behaviors.same
      
      case RandomWallet(oid, req, replyTo) =>
        log.info(s"random: oid=${oid}, req=${req}")
        val w = for {
          ss0 <- signer.random(oid)
          ss <- store.+++(ss0)
          w <- {
            val ws = ss.ws
            log.info(s"add: ${ss}")

            val signerData = signer.encodeSignerData(ss)
            
            Success(Wallet(ws.addr, ws.typ,ws.ts,ws.oid,signerData))
          }
        } yield w        
        
        w match {
          case Success(_) =>            
            replyTo ! w
          case Failure(e)=> 
            log.error(s"failed to create wallet: ${oid},${req}: ${e.getMessage}",e)
            replyTo ! Failure(e)
        }

        Behaviors.same
      
      case DeleteWallet(addr0, oid, replyTo) =>
        log.info(s"del: ${addr0}, oid=${oid}")
        val addr = addr0.toLowerCase()

        val ws = store.del(addr,oid)        
        ws match {
          case Success(ws) => 
            log.info(s"del: ${ws}")
            replyTo ! Success(Wallet(ws.addr, ws.typ, ws.ts,ws.oid))
          case Failure(e) => 
            log.error(s"failed to delete wallet: ${oid},${addr}: ${e.getMessage}",e)
            replyTo ! Failure(e)
        }
        
        Behaviors.same

      case SignWallet(addr0, oid, req, replyTo) =>
        log.info(s"sign: ${addr0}, oid=${oid}, req=${req}")            
        val addr = addr0.toLowerCase()

        val sig:Try[String] = for {
          chainId <- Blockchain.resolve(req.chain)
          ws0 <- store.???(addr,oid)

          web3 <- blockchains.getWeb3(chainId)
          nonceTx <- if(req.nonce == -1L) Eth.getNonce(addr)(web3) else Success(req.nonce)
          gasPrice <- if(req.gasPrice.isBlank()) Eth.getGasPrice()(web3) else Eth.strToWei(req.gasPrice)(web3)
          gasTip <- {
            if(req.gasTip.isBlank()) 
              Eth.strToWei(tips.get(req.chain))(web3) 
            else 
              Eth.strToWei(req.gasTip)(web3)
          }
          value <- Eth.strToWei(req.value.getOrElse("0"))(web3)
          
          b <- if(oid == None) Success(true) else Success(ws0.oid == oid)
          ws1 <- if(b) Success(ws0) else Failure(new Exception(s"not found: ${addr}"))          
          
          sig <- {
            log.info(s"sign: ${ws1}: ${req}")
            // signing by admin on behalf of another address/wallet is possible
            //signer.sign(ws1, req.to, nonceTx, req.data, gasPrice, gasTip, req.gasLimit, value, chainId)
            val signerData = signer.decodeSignerData(req.signerType,req.signerData)            
            val ss = SignerSecret(ws1,signerData)
            signer.sign(ss, SignerTxPayload(req.to, nonceTx, req.data, gasPrice, gasTip, req.gasLimit, value, chainId))
          }
        } yield sig
        
        sig match {
          case Success(sig) =>            
            replyTo ! Success(WalletSig(addr,sig))
          case Failure(e)=> 
            log.error(s"failed to sign transaction: ${oid},${addr},${req}: ${e.getMessage}",e)
            replyTo ! Failure(e)
        }

        Behaviors.same

      case TxWallet(addr0, oid, req, replyTo) =>
        log.info(s"tx: ${addr0}, oid=${oid}, req=${req}")            
        val addr = addr0.toLowerCase()

        val txHash:Try[String] = for {
          chainId <- Util.succeed(req.chain.getOrElse(Blockchain.ANVIL).asLong)
          ws0 <- store.???(addr,oid)
          
          web3 <- blockchains.getWeb3(chainId)
          nonceTx <- if(req.nonce == -1L) Eth.getNonce(addr)(web3) else Success(req.nonce)
          gasPrice <- if(req.gasPrice.isBlank()) Eth.getGasPrice()(web3) else Eth.strToWei(req.gasPrice)(web3)
          gasTip <- {
            //if(req.gasTip.isBlank()) Eth.strToWei(config.feeTip)(web3) else Eth.strToWei(req.gasTip)(web3)
            // ATTENTION: tip=0 is now value and not special case for ext-project !
            if(req.gasTip.isBlank()) 
              Eth.strToWei(tips.get(req.chain))(web3) 
            else 
              Eth.strToWei(req.gasTip)(web3)
          }
          value <- Eth.strToWei(req.value.getOrElse("0"))(web3)
          
          b <- if(oid == None) Success(true) else Success(ws0.oid == oid)
          ws1 <- if(b) Success(ws0) else Failure(new Exception(s"not found: ${addr}"))          
          
          sig <- {
            log.info(s"sign: ${ws1}: ${req}")
            // signing by admin on behalf of another address/wallet is possible
            //signer.sign(ws1, req.to, nonceTx, req.data, gasPrice, gasTip, req.gasLimit, value, chainId)
            val ss = SignerSecret(ws1,None)
            signer.sign(ss, SignerTxPayload(req.to, nonceTx, req.data, gasPrice, gasTip, req.gasLimit, value, chainId))
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
            log.error(s"failed to send transaction: ${oid},${addr},${req}: ${e.getMessage}",e)
            replyTo ! Failure(e)
        }

        Behaviors.same

      case CallWallet(addr0, oid, req, replyTo) =>
        log.info(s"call: ${addr0}, oid=${oid}, req=${req}")            
        val addr = addr0.toLowerCase()

        val result:Try[String] = for {
          chainId <- Util.succeed(req.chain.getOrElse(Blockchain.ANVIL).asLong)          
          web3 <- blockchains.getWeb3(chainId)
          value <- Eth.strToWei(req.value.getOrElse("0"))(web3)
                    
          result <- {
            Eth.call(addr,req.to,req.data,req.output)(web3)
          }
        } yield result
        
        result match {
          case Success(result) =>            
            replyTo ! Success(WalletCall(addr,result))
          case Failure(e)=> 
            log.warn(s"failed to call contract: ${oid},${addr},${req}: ${e.getMessage}",e)
            replyTo ! Failure(e)
        }

        Behaviors.same

      case BalanceWallet(addr0, oid, req, replyTo) => 
        log.info(s"balance: ${addr0}, oid=${oid}, req=${req}")            
        val addr = addr0.toLowerCase()

        def getBalance(addr:String, oid:Option[String], req:WalletBalanceReq, replyTo: ActorRef[Try[WalletBalance]]) = {
          val balances:Try[Seq[BlockchainBalance]] = for {
            
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
                // TODO: only EVM
                val chainId = b.id.toLong
                blockchains.getWeb3(chainId).toOption match {
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
              log.warn(s"failed to get balances: ${oid},${addr},${req}",e)
              replyTo ! Failure(e)
          }
        }

        // Helper function to create a timeout future
        def withTimeout[T](future: Future[T], timeout: FiniteDuration): Future[T] = {
          val timeoutFut = akka.pattern.after(timeout, using = system.classicSystem.scheduler) {
            Future.failed(new TimeoutException(s"Future timed out after ${timeout.toMillis}ms"))
          }
          Future.firstCompletedOf(Seq(future, timeoutFut))
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
                // TODO: only EVM
                val chainId = b.id.toLong
                blockchains.getWeb3(chainId).toOption match {
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
                
                withTimeout(Eth.getBalanceAsync(addr)(web3._2,ec),FiniteDuration(config.rpcTimeout,TimeUnit.MILLISECONDS))
                  .map(b => Success(b,web3._1))
                  .recover { 
                    case e: TimeoutException =>
                      log.warn(s"balance: ${addr}: Request timeout: ${web3._1}: ${e.getMessage}",e)
                      Failure(e)
                    case e => 
                      log.warn(s"balance: ${addr}: Request failed: ${web3._1}: ${e.getMessage}",e)
                      Failure(e)
                  }
              })
              Success(ff)
            }
            
            balances <- {
              val f = Future.sequence(ff) //Util.waitAll(ff)
              
              try {
                // ATTENTION: timeout must be smaller than akka actor timeout !
                Await.result(f,FiniteDuration(config.rpcTimeout + 1000L,TimeUnit.MILLISECONDS))
              } catch {
                case e:Exception => 
                  log.warn(s"timeout: ${oid},${addr},${req}: ${e.getMessage}",e)
              }
                            
              val bb = web3s.zip(ff).map{ case(web3,fbal) => {
                // future already timed out, so just get it
                val bal = Await.result(fbal,FiniteDuration(100L,TimeUnit.MILLISECONDS))
                  bal match {
                    case Success((bal,rpc)) => BlockchainBalance(web3._1.name,web3._1.id,bal)
                    case Failure(e) => 
                      log.warn(s"failed to get balance: ${oid},${addr},${web3}: ${e.getMessage}",e)
                      BlockchainBalance(web3._1.name,web3._1.id,-1,Some(e.getMessage))
                  }}
                }
              Success(bb)
            }
            
          } yield balances
          
          balances match {
            case Success(balances) =>            
              replyTo ! Success(WalletBalance(addr,balances))
            case Failure(e)=> 
              log.warn(s"failed to get balances: ${oid},${addr},${req}: ${e.getMessage}",e)
              replyTo ! Failure(e)
          }
        }

        log.info(s"asking balances: ${oid}: ${addr}: ${req.chains}")
        
        getBalanceAsync(addr,oid,req,replyTo)

        Behaviors.same

      case TxStatusAsk(txHash, oid:Option[String], req:TxStatusReq, replyTo) =>        
        log.info(s"status: ${txHash}, oid=${oid}, req=${req}")            

        val status:Try[String] = for {
          chainId <- Blockchain.resolve(req.chain)
          web3 <- blockchains.getWeb3(chainId)          
                    
          status <- {            
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
            log.warn(s"failed to get tx status: ${oid},${txHash},${req}: ${e.getMessage}",e)
            replyTo ! Failure(e)
        }

        Behaviors.same

      case TxCostAsk(addr0, oid, req, replyTo) =>
        log.info(s"tx estimate: ${addr0}, oid=${oid}, req=${req}")            
        val addr = addr0.toLowerCase()

        val r:Try[(BigInt,BigInt)] = for {
          chainId <- Blockchain.resolve(req.chain)
          ws0 <- store.???(addr,oid)
          
          web3 <- blockchains.getWeb3(chainId)          
          
          b <- if(oid == None) Success(true) else Success(ws0.oid == oid)
                    
          cost <- {
            Eth.estimateGas(addr,req.to,req.data)(web3)
          }
          price <- {
            Eth.getGasPrice()(web3)
          }
        } yield (cost,price)

        // fee tip should be dynamic
        val tip = Some(tips.get(req.chain))  //if(config.feeTip.isEmpty) None else Some(config.feeTip)
        
        r match {
          case Success((cost,price)) =>            
            replyTo ! Success(TxCost(cost,price,tip))
          case Failure(e)=> 
            log.warn(s"failed to estimate: ${oid},${addr},${req}: ${e.getMessage}",e)
            replyTo ! Failure(e)
        }

        Behaviors.same

      case GasPriceAsk(req, replyTo) =>
        log.info(s"gas price: req=${req}")            
        
        val r:Try[(BigInt,Long)] = for {
          chainId <- Blockchain.resolve(req.chain)
          
          web3 <- blockchains.getWeb3(chainId)          
                              
          price <- {
            Eth.getGasPrice()(web3)
          }
        } yield (price,chainId)
        
        r match {
          case Success(r) =>   
            val b = Blockchain.resolveById(r._2.toString).get
            replyTo ! Success(GasPrice(r._1, tok=b.tok, dec=b.dec))
          case Failure(e)=> 
            log.warn(s"failed to get gas price: ${req}: ${e.getMessage}",e)
            replyTo ! Failure(e)
        }

        Behaviors.same

      case SignWallet712(addr0, oid, req, replyTo) =>
        log.info(s"sign712: ${addr0}, oid=${oid}, req=${req}")            
        val addr = addr0.toLowerCase()

        val result:Try[String] = for {
          ws0 <- store.???(addr,oid)
          chainId <- Blockchain.resolve(req.chain)
          
          b <- if(oid == None) Success(true) else Success(ws0.oid == oid)
          ws1 <- if(b) Success(ws0) else Failure(new Exception(s"not found: ${addr}"))          
          
          sig <- {
            val ss = SignerSecret(ws1,None)
            signer.sign712(
              ss, 
              if(req.message.isDefined) 
                req.message.get
              else 
                Eth.toEIP712Message(
                  name = req.name.getOrElse(""),
                  version = req.version.getOrElse(""),
                  chainId = chainId,
                  verifyingContract = req.verifyingContract.getOrElse(""),
                  salt = req.salt,

                  types = req.types.getOrElse(Map()),
                  values = req.value.getOrElse(Map()),
                  primaryType = req.primaryType.getOrElse("")
                )
            )
          }        
        } yield sig

        result match {
          case Success(result) =>            
            replyTo ! Success(WalletSig(addr,result))
          case Failure(e)=> 
            log.warn(s"failed to sign712: ${oid},${addr},${req}: ${e.getMessage}",e)
            replyTo ! Failure(e)
        }

        Behaviors.same

    }
        
  }

}
