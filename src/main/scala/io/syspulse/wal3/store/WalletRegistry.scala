package io.syspulse.wal3.store

import scala.util.{Try,Success,Failure}

import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import io.syspulse.skel.Command

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
  
  def apply(store: WalletStore,signer:WalletSigner): Behavior[io.syspulse.skel.Command] = {
    registry(store,signer)
  }

  private def registry(store: WalletStore,signer:WalletSigner): Behavior[io.syspulse.skel.Command] = {    
    
    // Rules of oid
    // 1. if oid == None - this is access from admin/service account
    // 2. if oid == Some - this is access from user and needs to be validated
    Behaviors.receiveMessage {
      case GetWallets(oid, replyTo) =>
        val all = store.all(oid)
        val owned = if(oid==None) all else all.filter(ws => ws.oid == oid)
        replyTo ! Wallets(owned.map(ws => Wallet(ws.addr,ws.typ,ws.ts)),total = Some(all.size))
        Behaviors.same

      case GetWallet(addr, oid, replyTo) =>
        val ww = store.???(addr, oid)
        val owned = if(oid == None) ww else ww.filter(_.oid == oid)
        replyTo ! owned.map(ws => Wallet(ws.addr,ws.typ,ws.ts))
        Behaviors.same

      case CreateWallet(req, replyTo) =>
        
        // val store1 = 
        //   store.?(req.addr) match {
        //     case Success(_) => 
        //       replyTo ! Failure(new Exception(s"already exists: ${id}"))
        //       Success(store)
        //     case _ =>  
        //       val user = Wallet(id, req.email, req.name, req.xid, req.avatar, System.currentTimeMillis())
        //       val store1 = store.+(user)
        //       replyTo ! store1.map(_ => user)
        //       store1
        //   }
        
        Behaviors.same
      
      case RandomWallet(oid, replyTo) =>
        val w = for {
          ws <- signer.random(oid)
          _ <- store.+++(ws)
          w <- {
            Success(Wallet(ws.addr, ws.typ,ws.ts))
          }
        } yield w        
        
        w match {
          case Success(_) => replyTo ! w
          case Failure(e)=> replyTo ! Failure(e)
        }

        Behaviors.same
      
      case DeleteWallet(addr, oid, replyTo) =>
        val w = store.del(addr,oid)        
        w match {
          case Success(ws) => replyTo ! Success(Wallet(ws.addr, ws.typ, ws.ts))
          case Failure(e) => replyTo ! Failure(e)
        }
        Behaviors.same
    }
  }
}
