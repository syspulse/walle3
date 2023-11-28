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
import io.syspulse.skel.auth.permissions.rbac.Permissions
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
        
  def getWallets(oid:Option[UUID]): Future[Wallets] = registry.ask(GetWallets(oid, _))
  def getWallet(addr: String,oid:Option[UUID]): Future[Try[Wallet]] = registry.ask(GetWallet(addr, oid, _))
  
  def createWallet(req: WalletCreateReq): Future[Try[Wallet]] = registry.ask(CreateWallet(req, _))
  def deleteWallet(addr: String,oid:Option[UUID]): Future[Try[Wallet]] = registry.ask(DeleteWallet(addr,oid, _))
  def randomWallet(oid:Option[UUID]): Future[Try[Wallet]] = registry.ask(RandomWallet(oid,_))
  def signWallet(addr:String, oid:Option[UUID], req: WalletSignReq): Future[Try[WalletSignature]] = registry.ask(SignWallet(addr,oid,req, _))


  @GET @Path("/{addr}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Return Wallet by add",
    parameters = Array(new Parameter(name = "addr", in = ParameterIn.PATH, description = "Wallet addr")),
    responses = Array(new ApiResponse(responseCode="200",description = "Wallet returned",content=Array(new Content(schema=new Schema(implementation = classOf[Wallet])))))
  )
  def getWalletRoute(addr: String,oid:Option[UUID]) = get {
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
  def getWalletsRoute(oid:Option[UUID]) = get {
    metricGetCount.inc()
    complete(getWallets(oid))
  }

  @DELETE @Path("/{addr}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Delete Wallet by addr",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Wallet addr")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Wallet deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Wallet])))))
  )
  def deleteWalletRoute(addr: String,oid:Option[UUID]) = delete {
    onSuccess(deleteWallet(addr,oid)) { r =>
      metricDeleteCount.inc()      
      complete(StatusCodes.OK, r)
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Create Wallet",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WalletCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Wallet",content = Array(new Content(schema = new Schema(implementation = classOf[Wallet])))))
  )
  def createWalletRoute(oid:Option[UUID]) = post {
    entity(as[WalletCreateReq]) { req =>
      onSuccess(createWallet(req)) { r =>
        metricCreateCount.inc()
        complete(StatusCodes.Created, r)
      }
    }
  }

  def randomWalletRoute(oid:Option[UUID]) = post { 
    onSuccess(randomWallet(oid)) { r =>
      metricCreateCount.inc()
      complete(StatusCodes.Created, r)
    }
  }

  @POST @Path("/{addr}/sign") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("wallet"),summary = "Sign Transaction",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WalletSignReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Signature",content = Array(new Content(schema = new Schema(implementation = classOf[WalletSignature])))))
  )
  def signWalletRoute(addr:String,oid:Option[UUID]) = post {
    entity(as[WalletSignReq]) { req =>
      onSuccess(signWallet(addr,oid,req)) { r =>
        metricSignCount.inc()
        complete(r)
      }
    }
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
              if(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                getWalletsRoute(None) ~
                //createWalletRoute(None) ~
                randomWalletRoute(None)
              } else {
                getWalletsRoute(authn.getUser) ~
                //createWalletRoute(authn.getUser) ~
                randomWalletRoute(authn.getUser)
              }
            ),            
          )
        },
        pathPrefix(Segment) { addr => 
          pathPrefix("sign") {
            pathEndOrSingleSlash { 
              authenticate()(authn =>
                if(Permissions.isAdmin(authn) || Permissions.isService(authn)) 
                  signWalletRoute(addr,None)
                else
                  signWalletRoute(addr,authn.getUser)
              )
            }            
          } ~          
          pathEndOrSingleSlash {            
            authenticate()(authn =>
              if(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                getWalletRoute(addr,None) ~
                deleteWalletRoute(addr,None)
              } else {
                getWalletRoute(addr,authn.getUser) ~
                deleteWalletRoute(addr,authn.getUser)
              }
            ) 
          }
        }
      )
  }
}
