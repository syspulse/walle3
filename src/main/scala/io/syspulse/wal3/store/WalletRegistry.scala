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

object WalletRegistry {
  val log = Logger(s"${this}")
  // implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    
  final case class GetWallets(oid:Option[UUID],replyTo: ActorRef[Wallets]) extends Command
  final case class GetWallet(addr:String,oid:Option[UUID], replyTo: ActorRef[Try[Wallet]]) extends Command
  
  final case class CreateWallet(req: WalletCreateReq, replyTo: ActorRef[Try[Wallet]]) extends Command
  final case class RandomWallet(oid:Option[UUID], replyTo: ActorRef[Try[Wallet]]) extends Command

  final case class DeleteWallet(addr: String, oid:Option[UUID], replyTo: ActorRef[Try[Wallet]]) extends Command

  final case class SignWallet(addr: String, oid:Option[UUID], req: WalletSignReq, replyTo: ActorRef[Try[WalletSig]]) extends Command
  final case class TxWallet(addr: String, oid:Option[UUID], req: WalletTxReq, replyTo: ActorRef[Try[WalletTx]]) extends Command
  final case class BalanceWallet(addr: String, oid:Option[UUID], req: WalletBalanceReq, replyTo: ActorRef[Try[WalletBalance]]) extends Command
  
  def apply(store: WalletStore,signer:WalletSigner,blockchains:Blockchains): Behavior[io.syspulse.skel.Command] = {
    registry(store,signer,blockchains)
  }

  private def registry(store: WalletStore,signer:WalletSigner,blockchains:Blockchains): Behavior[io.syspulse.skel.Command] = {    
    
    // Rules of oid
    // 1. if oid == None - this is access from admin/service account
    // 2. if oid == Some - this is access from user and needs to be validated
    Behaviors.receiveMessage {
      case GetWallets(oid, replyTo) =>
        val all = store.all(oid)
        val owned = if(oid==None) all else all.filter(ws => ws.oid == oid)
        replyTo ! Wallets(
          owned.map(ws => {
            log.info(s"?: ${ws}")
            Wallet(ws.addr,ws.typ,ws.ts)
          }),
          total = Some(all.size)
        )
        Behaviors.same

      case GetWallet(addr, oid, replyTo) =>
        val ws = for {
          ws0 <- store.???(addr, oid)
          b <- if(oid == None) Success(true) else Success(ws0.oid == oid)
          ws1 <- if(b) Success(ws0) else Failure(new Exception(s"not found: ${addr}"))
        } yield ws1
        
        ws match {
          case Success(ws) =>
            log.info(s"?: ${ws}")
            replyTo ! Success(Wallet(ws.addr,ws.typ,ws.ts))
          case Failure(e)=> 
            replyTo ! Failure(e)
        }
        
        Behaviors.same

      case CreateWallet(req, replyTo) =>
        log.error(s"Not supported, use RandomWallet")        
        Behaviors.same
      
      case RandomWallet(oid, replyTo) =>        

        val w = for {
          ws <- signer.random(oid)
          _ <- store.+++(ws)
          w <- {
            log.info(s"add: ${ws}")

            Success(Wallet(ws.addr, ws.typ,ws.ts))
          }
        } yield w        
        
        w match {
          case Success(_) =>            
            replyTo ! w
          case Failure(e)=> replyTo ! Failure(e)
        }

        Behaviors.same
      
      case DeleteWallet(addr, oid, replyTo) =>
        val ws = store.del(addr,oid)        
        ws match {
          case Success(ws) => 
            log.info(s"del: ${ws}")
            replyTo ! Success(Wallet(ws.addr, ws.typ, ws.ts))
          case Failure(e) => replyTo ! Failure(e)
        }
        
        Behaviors.same

      case SignWallet(addr, oid, req, replyTo) =>        
        val sig:Try[String] = for {
          web3 <- blockchains.getWeb3(req.chainId.getOrElse(11155111))
          nonce <- Eth.getNonce(addr)(web3)
          gasPrice <- Eth.strToWei(req.gasPrice)(web3)
          gasTip <- Eth.strToWei(req.gasTip)(web3)
          value <- Eth.strToWei(req.value.getOrElse("0"))(web3)

          ws0 <- store.???(addr,oid)
          b <- if(oid == None) Success(true) else Success(ws0.oid == oid)
          ws1 <- if(b) Success(ws0) else Failure(new Exception(s"not found: ${addr}"))          
          
          sig <- {
            log.info(s"sign: ${ws1}: ${req}")
            // signing by admin on behalf of another address/wallet is possible
            signer.sign(ws1, req.to, req.nonce, req.data, gasPrice, gasTip, req.gasLimit, value, req.chainId.getOrElse(11155111))
          }
        } yield sig
        
        sig match {
          case Success(sig) =>            
            replyTo ! Success(WalletSig(addr,sig))
          case Failure(e)=> 
            replyTo ! Failure(e)
        }

        Behaviors.same

      case TxWallet(addr, oid, req, replyTo) =>        
        
        val txHash:Try[String] = for {
          web3 <- blockchains.getWeb3(req.chainId.getOrElse(11155111))
          gasPrice <- Eth.strToWei(req.gasPrice)(web3)
          gasTip <- Eth.strToWei(req.gasTip)(web3)
          value <- Eth.strToWei(req.value.getOrElse("0"))(web3)

          ws0 <- store.???(addr,oid)
          b <- if(oid == None) Success(true) else Success(ws0.oid == oid)
          ws1 <- if(b) Success(ws0) else Failure(new Exception(s"not found: ${addr}"))          
          
          sig <- {
            log.info(s"sign: ${ws1}: ${req}")
            // signing by admin on behalf of another address/wallet is possible
            signer.sign(ws1, req.to, req.nonce, req.data, gasPrice, gasTip, req.gasLimit, value, req.chainId.getOrElse(11155111))
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
            replyTo ! Failure(e)
        }

        Behaviors.same

      case BalanceWallet(addr, oid, req, replyTo) =>        
        
        val balances:Try[Seq[BlockchainBalance]] = for {
          // ws0 <- store.???(addr,oid)
          // b <- if(oid == None) Success(true) else Success(ws0.oid == oid)
          // ws1 <- if(b) Success(ws0) else Failure(new Exception(s"not found: ${addr}"))
          
          web3s <- {
            val bb:Seq[Blockchain] = if(req.blockchains.size == 0) 
              blockchains.all()
            else 
              req.blockchains.flatMap(b => {
                if(b.size > 0 && b(0).isDigit)
                  blockchains.get(b.toLong)
                else
                  blockchains.getByName(b)
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
            replyTo ! Failure(e)
        }

        Behaviors.same
    }
  }
}
