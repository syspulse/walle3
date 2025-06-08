package io.syspulse.wal3.server

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}
import java.nio.file.Paths

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.FileIO

import scala.annotation.tailrec
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
import io.syspulse.skel.auth.permissions.rbac
import io.syspulse.skel.util.Util
import io.syspulse.skel.blockchain.Blockchain
import io.syspulse.skel.auth._
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.RouteAuthorizers

import io.syspulse.wal3._
import io.syspulse.wal3.store.WalletRegistry
import io.syspulse.wal3.store.WalletRegistry._
import io.syspulse.wal3.server._
import io.syspulse.skel.service.telemetry.TelemetryRegistry


object WalletRoutes {
  def getOwner(authn:Authenticated)(implicit config:Config):Option[String] = {
    val t = authn.getToken
    if(!t.isDefined) return None
    
    val json = ujson.read(t.get.claim.content)
    json(config.ownerAttr).strOpt match {
      case Some(tid) => Some(tid)
      case None => 
        log.warn(s"missing JWT attribute: ${config.ownerAttr}")
        None
    }
  }

  def isAdminRole(authn:Authenticated)(implicit config:Config):Boolean = {
    isRole(authn,config.adminRole)
  }

  def isServiceRole(authn:Authenticated)(implicit config:Config):Boolean = {
    isRole(authn,config.serviceRole)
  }

  def isRole(authn:Authenticated,role:String):Boolean = {
    val t = authn.getToken
    if(!t.isDefined) return false
    
    Util.parseJson(t.get.claim.content,role) match {
      case Success(r) => 
        (r.size >0 && r(0) != "")
      case Failure(e) => 
        log.debug(s"JWT Role attribute not found: ${role}: ${e.getMessage()}")
        false
    }
  }
}

// ==== Strict Permissions ==============================================================================================================
class PermissionsRbacStrict()(implicit config:Config) extends Permissions {  
 
  def isAdmin(authn:Authenticated):Boolean = Permissions.isGod || WalletRoutes.isAdminRole(authn)
  def isService(authn:Authenticated):Boolean = Permissions.isGod || WalletRoutes.isServiceRole(authn)
  // not supported mapping to User, only Service Account
  def isUser(id:UUID,authn:Authenticated):Boolean = false
  def isAllowed(authn:Authenticated,resource:String,action:String):Boolean = false
  def hasRole(authn:Authenticated,role:String):Boolean = Permissions.isGod || WalletRoutes.isRole(authn,role)
}

// ======================================================================================================================================
@Path("/")
class WalletRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_],config:Config) extends CommonRoutes with Routeable 
  with RouteAuthorizers {
  
  implicit val system: ActorSystem[_] = context.system
  
  implicit val permissions = if(config.permissions == "strict") new PermissionsRbacStrict() else Permissions(config.permissions)

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WalletJson._
  
  val metricGetCount: Counter = Counter.build().name("wal3_get_total").help("wal3 gets").register(TelemetryRegistry.registry)
  val metricCreateCount: Counter = Counter.build().name("wal3_create_total").help("wal3 creats and random creates").register(TelemetryRegistry.registry)
  val metricDeleteCount: Counter = Counter.build().name("wal3_delete_total").help("wal3 deletes").register(TelemetryRegistry.registry)
  val metricSignCount: Counter = Counter.build().name("wal3_sign_total").help("wal3 signs").register(TelemetryRegistry.registry)
  val metricTxCount: Counter = Counter.build().name("wal3_tx_total").help("wal3 transactions").register(TelemetryRegistry.registry)
  val metricBalanceCount: Counter = Counter.build().name("wal3_balance_total").help("wal3 balances").register(TelemetryRegistry.registry)
  val metricCallCount: Counter = Counter.build().name("wal3_call_total").help("wal3 calls").register(TelemetryRegistry.registry)
        
  def getWallets(oid:Option[String]): Future[Wallets] = registry.ask(GetWallets(oid, _))
  def getWallet(addr: String,oid:Option[String]): Future[Try[Wallet]] = registry.ask(GetWallet(addr, oid, _))
  
  def createWallet(oid:Option[String],req: WalletCreateReq): Future[Try[Wallet]] = registry.ask(CreateWallet(oid,req, _))
  def deleteWallet(addr: String,oid:Option[String]): Future[Try[Wallet]] = registry.ask(DeleteWallet(addr,oid, _))
  def randomWallet(oid:Option[String],req: WalletRandomReq): Future[Try[Wallet]] = registry.ask(RandomWallet(oid,req,_))
  
  def signWallet(addr:String, oid:Option[String], req: WalletSignReq): Future[Try[WalletSig]] = registry.ask(SignWallet(addr,oid,req, _))
  def txWallet(addr:String, oid:Option[String], req: WalletTxReq): Future[Try[WalletTx]] = registry.ask(TxWallet(addr,oid,req, _))
  def balanceWallet(addr:String, oid:Option[String], req: WalletBalanceReq): Future[Try[WalletBalance]] = registry.ask(BalanceWallet(addr,oid,req, _))
  def txStatus(txHash:String, oid:Option[String], req: TxStatusReq): Future[Try[TxStatus]] = registry.ask(TxStatusAsk(txHash,oid,req, _))
  def txCost(addr:String, oid:Option[String], req: TxCostReq): Future[Try[TxCost]] = registry.ask(TxCostAsk(addr,oid,req, _))
  def blockchainPrice(req: BlockchainReq): Future[Try[GasPrice]] = registry.ask(GasPriceAsk(req, _))
  def signWallet712(addr:String, oid:Option[String], req: WalletSign712Req): Future[Try[WalletSig]] = registry.ask(SignWallet712(addr,oid,req, _))
  def callWallet(addr:String, oid:Option[String], req: WalletCallReq): Future[Try[WalletCall]] = registry.ask(CallWallet(addr,oid,req, _))

  @GET @Path("/owner/{oid}/{addr}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Return Wallet by Address",
    parameters = Array(
      new Parameter(name = "oid", in = ParameterIn.PATH, description = "Wallet owner"),
      new Parameter(name = "addr", in = ParameterIn.PATH, description = "Wallet address")),
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

  @GET @Path("/owner/{oid}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"), summary = "Return all Wallets for owner",
  parameters = Array(
      new Parameter(name = "oid", in = ParameterIn.PATH, description = "Wallet owner")),    
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Wallets",content = Array(new Content(schema = new Schema(implementation = classOf[Wallets])))))
  )
  def getWalletsRoute(oid:Option[String]) = get {
    metricGetCount.inc()
    complete(getWallets(oid))
  }

  @DELETE @Path("/owner/{oid}/{addr}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Delete Wallet by addr of owner",
    parameters = Array(
      new Parameter(name = "oid", in = ParameterIn.PATH, description = "Wallet owner")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Wallet deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Wallet])))))
  )
  def deleteWalletRoute(addr: String,oid:Option[String]) = delete {
    onSuccess(deleteWallet(addr,oid)) { r =>
      metricDeleteCount.inc()      
      complete(StatusCodes.OK, r)
    }
  }

  @POST @Path("/owner/{oid}") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Create Wallet",
    parameters = Array(new Parameter(name = "oid", in = ParameterIn.PATH, description = "Wallet owner")),
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

  @POST @Path("/owner/{oid}/random") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Create Random Wallet",
    parameters = Array(new Parameter(name = "oid", in = ParameterIn.PATH, description = "Wallet owner")),
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

  @POST @Path("/owner/{oid}/{addr}/sign") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Sign Transaction",
    parameters = Array(
      new Parameter(name = "oid", in = ParameterIn.PATH, description = "Wallet owner"),
      new Parameter(name = "addr", in = ParameterIn.PATH, description = "Wallet address")),
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

  @POST @Path("/owner/{oid}/{addr}/tx") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Send Transaction",
    parameters = Array(
      new Parameter(name = "oid", in = ParameterIn.PATH, description = "Wallet owner"),
      new Parameter(name = "addr", in = ParameterIn.PATH, description = "Wallet address")),
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

  @POST @Path("/owner/{oid}/{addr}/call") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Call contract (not transaction)",
    parameters = Array(
      new Parameter(name = "oid", in = ParameterIn.PATH, description = "Wallet owner"),
      new Parameter(name = "addr", in = ParameterIn.PATH, description = "Wallet address"),
      new Parameter(name = "to", in = ParameterIn.PATH, description = "Contract address"),
      new Parameter(name = "data", in = ParameterIn.PATH, description = "Encoded function"),
      new Parameter(name = "output", in = ParameterIn.PATH, description = "Output type (address,string,uint256,...)"),
    ),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WalletCallReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "result output",content = Array(new Content(schema = new Schema(implementation = classOf[WalletCall])))))
  )
  def callWalletRoute(addr:String,oid:Option[String]) = post {
    entity(as[WalletCallReq]) { req =>
      onSuccess(callWallet(addr,oid.orElse(req.oid),req)) { r =>
        metricCallCount.inc()
        complete(r)
      }
    }
  }


  @GET @Path("/owner/{oid}/{addr}/balance/{blockchain}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"), summary = "Return all Wallet balances",
    parameters = Array(
      new Parameter(name = "oid", in = ParameterIn.PATH, description = "Wallet owner"),
      new Parameter(name = "addr", in = ParameterIn.PATH, description = "Wallet address")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Balances",content = Array(new Content(schema = new Schema(implementation = classOf[WalletBalance])))))
  )
  def getWalletBalanceRoute(addr:String,oid:Option[String],blockchain:Option[String]) = get {
    metricBalanceCount.inc()
    complete(balanceWallet(addr,oid, WalletBalanceReq(oid,chains = if(blockchain.isDefined) blockchain.get.split(",").toIndexedSeq.map(Blockchain(_)) else Seq())))
  }
  
  @GET @Path("/owner/{oid}/{addr}/tx/{hash}/{blockchain}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"), summary = "Return Transaction status",
    parameters = Array(
      new Parameter(name = "oid", in = ParameterIn.PATH, description = "Wallet owner"),
      new Parameter(name = "addr", in = ParameterIn.PATH, description = "Wallet address (Address is ignored and used only fro API consistency)"),
      new Parameter(name = "hash", in = ParameterIn.PATH, description = "Transaction hash"),
      new Parameter(name = "blockchain", in = ParameterIn.PATH, description = "Blockchain name or id")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Transaction status",content = Array(new Content(schema = new Schema(implementation = classOf[TxStatus])))))
  )
  def getTxStatusRoute(txHash:String,addr:String,oid:Option[String],blockchain:String) = get {
    metricBalanceCount.inc()
    complete(txStatus(txHash, oid, TxStatusReq(oid, chain = Blockchain.resolve(blockchain))))
  }

  @PUT @Path("/owner/{oid}/{addr}/cost") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"), summary = "Estimate contract call gas costs and current gas price",
    parameters = Array(
      new Parameter(name = "oid", in = ParameterIn.PATH, description = "Wallet owner"),
      new Parameter(name = "addr", in = ParameterIn.PATH, description = "Wallet address which executes call")
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Costs",content = Array(new Content(schema = new Schema(implementation = classOf[TxCost])))))
  )
  def getTxCostRoute(addr:String,oid:Option[String]) = put {
   entity(as[TxCostReq]) { req =>
      onSuccess(txCost(addr,oid.orElse(req.oid),req)) { r =>
        metricTxCount.inc()
        complete(r)
      }
    }
  }

  @GET @Path("/blockchain/{blockchain}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"), summary = "Return Blockchain Gas price",
    parameters = Array(
      new Parameter(name = "blockchain", in = ParameterIn.PATH, description = "Blockchain name or id")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Gas price",content = Array(new Content(schema = new Schema(implementation = classOf[GasPrice])))))
  )
  def geBlockchainPriceRoute(blockchain:String) = get {    
    complete(blockchainPrice(BlockchainReq(chain = Blockchain.resolve(blockchain))))
  }

  @POST @Path("/owner/{oid}/{addr}/sign712") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Sign EIP-712 message",
    parameters = Array(
      new Parameter(name = "oid", in = ParameterIn.PATH, description = "Wallet owner"),
      new Parameter(name = "addr", in = ParameterIn.PATH, description = "Wallet address")),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WalletSign712Req])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Signature",content = Array(new Content(schema = new Schema(implementation = classOf[WalletSig])))))
  )
  def signWallet712Route(addr:String,oid:Option[String]) = post {
    entity(as[WalletSign712Req]) { req =>
      onSuccess(signWallet712(addr,oid.orElse(req.oid),req)) { r =>
        metricSignCount.inc()
        complete(r)
      }
    }
  }
  
// =======================================================================================================================================================
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

      pathPrefix("blockchain") { 
        authenticate()(authn =>
          pathEndOrSingleSlash { 
            geBlockchainPriceRoute("ethereum")
          } ~
          pathPrefix(Segment) { blockchain => 
            geBlockchainPriceRoute(blockchain)
          }
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
          pathPrefix("sign712") {
            pathEndOrSingleSlash { 
              authenticate()(authn =>
                if(Permissions.isAdmin(authn) || Permissions.isService(authn)) 
                  signWallet712Route(addr,None)
                else
                  signWallet712Route(addr,authn.getUser.map(_.toString))
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
        } ~ 
          pathEndOrSingleSlash {            
            authenticate()(authn =>
              if(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                getWalletsRoute(None)
              } else {
                getWalletsRoute(authn.getUser.map(_.toString))                
              }
            ) 
          }
      },

      // --------------------------------- Tenant Wallet
      pathPrefix("tenant") { 
        pathPrefix("random") {
          pathEndOrSingleSlash { 
            authenticate()(authn =>
              if(Permissions.isAdmin(authn) || Permissions.isService(authn)) 
                randomWalletRoute(None)
              else
                randomWalletRoute(WalletRoutes.getOwner(authn))
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
                  signWalletRoute(addr,WalletRoutes.getOwner(authn))
              )
            }
          } ~
          pathPrefix("sign712") {
            pathEndOrSingleSlash { 
              authenticate()(authn =>
                if(Permissions.isAdmin(authn) || Permissions.isService(authn)) 
                  signWallet712Route(addr,None)
                else
                  signWallet712Route(addr,WalletRoutes.getOwner(authn))
              )
            }
          } ~
          pathPrefix("tx") {
            pathPrefix(Segment) { txHash => 
              pathPrefix(Segment) { blockchain => 
                authenticate()(authn => {
                  if(Permissions.isAdmin(authn) || Permissions.isService(authn)) 
                      getTxStatusRoute(txHash,addr,None,blockchain)
                    else
                      getTxStatusRoute(txHash,addr,WalletRoutes.getOwner(authn),blockchain)
                })
              }
            } ~
            pathEndOrSingleSlash { 
              authenticate()(authn =>
                if(Permissions.isAdmin(authn) || Permissions.isService(authn)) 
                  txWalletRoute(addr,None)
                else
                  txWalletRoute(addr,WalletRoutes.getOwner(authn))
              )
            }
          } ~
          pathPrefix("balance") {
            pathPrefix(Segment) { blockchain => 
              authenticate()(authn => {
                if(Permissions.isAdmin(authn) || Permissions.isService(authn)) 
                    getWalletBalanceRoute(addr,None,Some(blockchain))
                  else
                    getWalletBalanceRoute(addr,WalletRoutes.getOwner(authn),Some(blockchain))
              })
            } ~
            pathEndOrSingleSlash { 
              authenticate()(authn =>
                if(Permissions.isAdmin(authn) || Permissions.isService(authn)) 
                  getWalletBalanceRoute(addr,None,None)
                else
                  getWalletBalanceRoute(addr,WalletRoutes.getOwner(authn),None)
              )
            }
          } ~
          pathEndOrSingleSlash {            
            authenticate()(authn =>
              if(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                getWalletRoute(addr,None) ~
                deleteWalletRoute(addr,None)
              } else {
                getWalletRoute(addr,WalletRoutes.getOwner(authn)) ~
                deleteWalletRoute(addr,WalletRoutes.getOwner(authn))
              }
            ) 
          }
        } ~
          pathEndOrSingleSlash {            
            authenticate()(authn =>
              if(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                createWalletRoute(None) ~
                getWalletsRoute(None)
              } else {
                createWalletRoute(WalletRoutes.getOwner(authn)) ~
                getWalletsRoute(WalletRoutes.getOwner(authn))
              }
            ) 
          }
      },

      // ----------------------------------- owner (OwnerID) Wallet 
      pathPrefix("owner") {         
        pathPrefix(Segment) { oid => concat(
          pathPrefix("random") {            
            pathEndOrSingleSlash {               
              authenticate()(authn => {
                authorize(Permissions.isAdmin(authn) || Permissions.isService(authn) || WalletRoutes.isServiceRole(authn)) {                  
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
            pathPrefix("sign712") {
              pathEndOrSingleSlash { 
                authenticate()(authn => authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                  signWallet712Route(addr,Some(oid))
                })
              }
            } ~
            pathPrefix("tx") {
              pathPrefix(Segment) { txHash => 
                pathPrefix(Segment) { blockchain => 
                  authenticate()(authn => {
                    if(Permissions.isAdmin(authn) || Permissions.isService(authn)) 
                        getTxStatusRoute(txHash,addr,None,blockchain)
                      else
                        getTxStatusRoute(txHash,addr,WalletRoutes.getOwner(authn),blockchain)
                  })
                }
              } ~
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
            pathPrefix("cost") {
              pathEndOrSingleSlash { 
                authenticate()(authn => authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                  getTxCostRoute(addr,Some(oid))
                })
              }
            } ~
            pathPrefix("call") {
              pathEndOrSingleSlash { 
                authenticate()(authn => authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                  callWalletRoute(addr,Some(oid))
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
