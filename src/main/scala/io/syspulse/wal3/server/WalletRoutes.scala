package io.syspulse.wal3.server

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}
import java.nio.file.Paths

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.FileIO

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.model.StatusCodes._

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import io.swagger.v3.oas.annotations.parameters.RequestBody
// import javax.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
// import javax.ws.rs.core.MediaType
import jakarta.ws.rs.{Consumes, POST, PUT, GET, DELETE, Path, Produces}
import jakarta.ws.rs.core.MediaType


import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.CommonRoutes

import io.syspulse.skel.Command

import io.syspulse.skel.auth._
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.RouteAuthorizers

import io.syspulse.wal3._
import io.syspulse.wal3.store.WalletRegistry
import io.syspulse.wal3.store.WalletRegistry._
import io.syspulse.wal3.server._
import io.syspulse.skel.service.telemetry.TelemetryRegistry

@Path("/")
class WalletRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_],config:Config) extends CommonRoutes with Routeable 
  with RouteAuthorizers {
  
  implicit val system: ActorSystem[_] = context.system
  
  implicit val permissions = Permissions()

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WalletJson._
  
  val metricGetCount: Counter = Counter.build().name("wal3_get_total").help("wal3 gets").register(TelemetryRegistry.registry)
  val metricCreateCount: Counter = Counter.build().name("wal3_create_total").help("wal3 posts").register(TelemetryRegistry.registry)
  val metricDeleteCount: Counter = Counter.build().name("wal3_delete_total").help("wal3 deletes").register(TelemetryRegistry.registry)
  val metricSignCount: Counter = Counter.build().name("wal3_sign_total").help("wal3 signs").register(TelemetryRegistry.registry)
  val metricTxCount: Counter = Counter.build().name("wal3_tx_total").help("wal3 transactions").register(TelemetryRegistry.registry)
  val metricBalanceCount: Counter = Counter.build().name("wal3_balance_total").help("wal3 balances").register(TelemetryRegistry.registry)
        
  def getWallets(oid:Option[String]): Future[Wallets] = registry.ask(GetWallets(oid, _))
  def getWallet(addr: String,oid:Option[String]): Future[Try[Wallet]] = registry.ask(GetWallet(addr, oid, _))
  
  def createWallet(oid:Option[String],req: WalletCreateReq): Future[Try[Wallet]] = registry.ask(CreateWallet(oid,req, _))
  def deleteWallet(addr: String,oid:Option[String]): Future[Try[Wallet]] = registry.ask(DeleteWallet(addr,oid, _))
  def randomWallet(oid:Option[String],req: WalletRandomReq): Future[Try[Wallet]] = registry.ask(RandomWallet(oid,req,_))
  
  def signWallet(addr:String, oid:Option[String], req: WalletSignReq): Future[Try[WalletSig]] = registry.ask(SignWallet(addr,oid,req, _))
  def txWallet(addr:String, oid:Option[String], req: WalletTxReq): Future[Try[WalletTx]] = registry.ask(TxWallet(addr,oid,req, _))
  def balanceWallet(addr:String, oid:Option[String], req: WalletBalanceReq): Future[Try[WalletBalance]] = registry.ask(BalanceWallet(addr,oid,req, _))


  @GET @Path("/{addr}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Return Wallet by add",
    parameters = Array(new Parameter(name = "addr", in = ParameterIn.PATH, description = "Wallet addr")),
    responses = Array(new ApiResponse(responseCode="200",description = "Wallet returned",content=Array(new Content(schema=new Schema(implementation = classOf[Wallet])))))
  )
  def getWalletRoute(addr: String, oid:Option[String]) = get {
    rejectEmptyResponse {
      onSuccess(getWallet(addr,oid)) { r =>
        metricGetCount.inc()
        complete(r)
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"), summary = "Return all Wallets",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Wallets",content = Array(new Content(schema = new Schema(implementation = classOf[Wallets])))))
  )
  def getWalletsRoute(oid:Option[String]) = get {
    metricGetCount.inc()
    complete(getWallets(oid))
  }

  @DELETE @Path("/tenant/{oid}/{addr}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Delete Wallet by addr",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Wallet addr")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Wallet deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Wallet])))))
  )
  def deleteWalletRoute(addr: String,oid:Option[String]) = delete {
    onSuccess(deleteWallet(addr,oid)) { r =>
      metricDeleteCount.inc()      
      complete(StatusCodes.OK, r)
    }
  }

  @POST @Path("/tenant/{oid}") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Create Wallet",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WalletCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Wallet",content = Array(new Content(schema = new Schema(implementation = classOf[Wallet])))))
  )
  def createWalletRoute(oid:Option[String]) = post {
    entity(as[WalletCreateReq]) { req =>
      onSuccess(createWallet(oid.orElse(req.oid),req)) { r =>
        metricCreateCount.inc()
        complete(StatusCodes.Created, r)
      }
    }
  }

  @POST @Path("/tenant/{oid}/random") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Create Random Wallet",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WalletRandomReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Wallet",content = Array(new Content(schema = new Schema(implementation = classOf[Wallet])))))
  )
  def randomWalletRoute(oid:Option[String]) = post { 
    entity(as[WalletRandomReq]) { req =>
      onSuccess(randomWallet(oid.orElse(req.oid),req)) { r =>
        metricCreateCount.inc()
        complete(StatusCodes.Created, r)
      }
    }
  }

  @POST @Path("/tenant/{oid}/{addr}/sign") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Sign Transaction",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WalletSignReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Signature",content = Array(new Content(schema = new Schema(implementation = classOf[WalletSig])))))
  )
  def signWalletRoute(addr:String,oid:Option[String]) = post {
    entity(as[WalletSignReq]) { req =>
      onSuccess(signWallet(addr,oid.orElse(req.oid),req)) { r =>
        metricSignCount.inc()
        complete(r)
      }
    }
  }

  @POST @Path("/tenant/{oid}/{addr}/tx") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Send Transaction",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WalletTxReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Transaction Hash",content = Array(new Content(schema = new Schema(implementation = classOf[WalletTx])))))
  )
  def txWalletRoute(addr:String,oid:Option[String]) = post {
    entity(as[WalletTxReq]) { req =>
      onSuccess(txWallet(addr,oid.orElse(req.oid),req)) { r =>
        metricTxCount.inc()
        complete(r)
      }
    }
  }

  @GET @Path("/tenant/{oid}/{addr}/balance") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"), summary = "Return all Wallet balances",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Balances",content = Array(new Content(schema = new Schema(implementation = classOf[WalletBalance])))))
  )
  def getWalletBalanceRoute(addr:String,oid:Option[String],blockchain:Option[String]) = get {
    metricBalanceCount.inc()
    complete(balanceWallet(addr,oid, WalletBalanceReq(oid,blockchains = if(blockchain.isDefined) blockchain.get.split(",").toSeq else Seq())))
  }
  
  val corsAllow = CorsSettings(system.classicSystem)
    //.withAllowGenericHttpRequests(true)
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(HttpMethods.OPTIONS,HttpMethods.GET,HttpMethods.POST,HttpMethods.PUT,HttpMethods.DELETE,HttpMethods.HEAD))

  override def routes: Route = cors(corsAllow) {
    concat(
      pathEndOrSingleSlash {
        concat(
          authenticate()(authn => 
            authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
              getWalletsRoute(None) ~
              createWalletRoute(None)                
            // } 
            // else {
            //   getWalletsRoute(authn.getUser)
            // }
          })   
        )
      },

      // --------------------------------- User Wallet
      pathPrefix("user") { 
        pathPrefix("random") {
          pathEndOrSingleSlash { 
            authenticate()(authn =>
              if(Permissions.isAdmin(authn) || Permissions.isService(authn)) 
                randomWalletRoute(None)
              else
                randomWalletRoute(authn.getUser.map(_.toString))
            )
          }
        } ~
        pathPrefix(Segment) { addr => 
          pathPrefix("sign") {
            pathEndOrSingleSlash { 
              authenticate()(authn =>
                if(Permissions.isAdmin(authn) || Permissions.isService(authn)) 
                  signWalletRoute(addr,None)
                else
                  signWalletRoute(addr,authn.getUser.map(_.toString))
              )
            }
          } ~
          pathPrefix("tx") {
            pathEndOrSingleSlash { 
              authenticate()(authn =>
                if(Permissions.isAdmin(authn) || Permissions.isService(authn)) 
                  txWalletRoute(addr,None)
                else
                  txWalletRoute(addr,authn.getUser.map(_.toString))
              )
            }
          } ~
          pathPrefix("balance") {
            pathPrefix(Segment) { blockchain => 
              authenticate()(authn => {
                if(Permissions.isAdmin(authn) || Permissions.isService(authn)) 
                    getWalletBalanceRoute(addr,None,Some(blockchain))
                  else
                    getWalletBalanceRoute(addr,authn.getUser.map(_.toString),Some(blockchain))
              })
            } ~
            pathEndOrSingleSlash { 
              authenticate()(authn =>
                if(Permissions.isAdmin(authn) || Permissions.isService(authn)) 
                  getWalletBalanceRoute(addr,None,None)
                else
                  getWalletBalanceRoute(addr,authn.getUser.map(_.toString),None)
              )
            }
          } ~
          pathEndOrSingleSlash {            
            authenticate()(authn =>
              if(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                getWalletRoute(addr,None) ~
                deleteWalletRoute(addr,None)
              } else {
                getWalletRoute(addr,authn.getUser.map(_.toString)) ~
                deleteWalletRoute(addr,authn.getUser.map(_.toString))
              }
            ) 
          }
        }
      },

      // ----------------------------------- Tenant (OwnerID) Wallet 
      pathPrefix("tenant") {         
        pathPrefix(Segment) { oid => concat(
          pathPrefix("random") {            
            pathEndOrSingleSlash {               
              authenticate()(authn => {
                authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {                  
                  randomWalletRoute(Some(oid))
              }})
            }
          },
          pathPrefix(Segment) { addr => 
            pathPrefix("sign") {
              pathEndOrSingleSlash { 
                authenticate()(authn => authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                  signWalletRoute(addr,Some(oid))
                })
              }
            } ~
            pathPrefix("tx") {
              pathEndOrSingleSlash { 
                authenticate()(authn => authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                    txWalletRoute(addr,Some(oid))
                })
              }
            } ~
            pathPrefix("balance") {
              pathPrefix(Segment) { blockchain => 
                authenticate()(authn => authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                  getWalletBalanceRoute(addr,Some(oid),Some(blockchain))
                })
              } ~
              pathEndOrSingleSlash { 
                authenticate()(authn => authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                  getWalletBalanceRoute(addr,Some(oid),None)
                })
              }
            } ~
            pathEndOrSingleSlash {            
              authenticate()(authn => authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                getWalletRoute(addr,Some(oid)) ~
                deleteWalletRoute(addr,Some(oid))
              }) 
            }
          },
          pathEndOrSingleSlash {            
            authenticate()(authn => authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
              createWalletRoute(Some(oid)) ~
              getWalletsRoute(Some(oid))   
            })
          }
        )}
      }
    )      
  }
}
