package io.syspulse.wal3

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.auth.jwt.AuthJwt

import io.jvm.uuid._

import io.syspulse.skel.FutureAwaitable._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

import io.syspulse.wal3._
import io.syspulse.wal3.signer._
import io.syspulse.wal3.store._
import io.syspulse.wal3.server._
import io.syspulse.wal3.cypher._

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/wal3",

  datastore:String = "mem://",
  signer:String = "eth1://",
  cypher:String = "key://",
  blockchains:Seq[String] = Seq(),

  jwtUri:String = "hs512://",
      
  cmd:String = "server",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {
  
  def main(args:Array[String]):Unit = {
    Console.err.println(s"args: '${args.mkString(",")}'")

    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"walle3","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),

        ArgString('d', "datastore",s"Datastore [cache://,dir://,postgres://,mysql://,kms://] (def: ${d.datastore})"),
        ArgString('s', "signer",s"Signer [eth1://,kms://] (def: ${d.signer})"),
        ArgString('c', "cypher",s"Cypher [pass://] (def: ${d.cypher})"),
        ArgString('b', "blockchains",s"Blockchains [id1=http://rpc1,id2=http://rpc2] (def: ${d.blockchains})"),
        
        ArgString('_', "jwt.uri",s"JWT Uri [hs512://secret,rs512://pk/key,rs512://sk/key] (def: ${d.jwtUri})"),
                
        ArgCmd("server","Command"),        
        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    implicit val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      
      datastore = c.getString("datastore").getOrElse(d.datastore),
      signer = c.getString("signer").getOrElse(d.signer),
      cypher = c.getString("cypher").getOrElse(d.cypher),
      blockchains = c.getListString("blockchains",d.blockchains),

      jwtUri = c.getString("jwt.uri").getOrElse(d.jwtUri),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    if(! config.jwtUri.isBlank()) {
      AuthJwt(config.jwtUri)
    }

    val blockchains = Blockchains(config.blockchains)

    val cypher = config.cypher.split("://").toList match {
      case "none" :: prefix :: _ => new CypherNone(prefix)
      case "none" :: Nil => new CypherNone("")
      
      case "key" :: key :: Nil => new CypherKey(key)
      case "key" :: Nil => new CypherKey("")

      case "file" :: file :: Nil => new CypherFile(file)
      case "file" :: Nil => new CypherFile("")

      case "kms" :: keyId :: _ => new CypherKMS(keyId)
      case _ => {        
        Console.err.println(s"Uknown cypher: '${config.cypher}'")
        sys.exit(1)
      }
    }    

    var signer = config.signer.split("://").toList match {
      case "eth1" ::  uri => new WalletSignerEth1(cypher,blockchains)
      case "kms" ::  uri => new WalletStoreKMS(blockchains,uri.mkString("://"))
      case _ => {        
        Console.err.println(s"Uknown signer: '${config.signer}'")
        sys.exit(1)
      }
    }    

    val store = config.datastore.split("://").toList match {
      // WARNING: kms signer must be also StoreKMS
      case "kms" :: uri => 
        
        if( !signer.isInstanceOf[WalletStoreKMS]) {
          log.warn(s"KMS store supports only kms:// signer: creating default KMS signer")
          signer = new WalletStoreKMS(blockchains,uri.mkString("://"))
          signer
        } 
        signer.asInstanceOf[WalletStoreKMS] //new WalletStoreKMS(blockchains,config.tag)

      case "dir" :: Nil => new WalletStoreDir()
      case "dir" :: dir :: _ => new WalletStoreDir(dir)

      case "postgres" :: Nil => new WalletStoreDB(c,s"postgres://postgres")
      case "postgres" :: db :: Nil => new WalletStoreDB(c,s"postgres://${db}")
      
      case "mysql" :: Nil => new WalletStoreDB(c,s"mysql://mysql")
      case "mysql" :: db :: Nil => new WalletStoreDB(c,s"mysql://${db}")

      case "jdbc" :: db :: Nil => new WalletStoreDB(c,s"mysql://${db}")
      case "jdbc" :: typ :: db :: Nil => new WalletStoreDB(c,s"${typ}://${db}")

      case "mem" :: _ => new WalletStoreMem()
      case _ => 
        Console.err.println(s"Uknown datastore: '${config.datastore}'")
        sys.exit(1)      
    }

    Console.err.println(s"Cypher: ${cypher}")
    Console.err.println(s"Signer: ${signer}")
    Console.err.println(s"Store: ${store}")
    Console.err.println(s"Blockchains: ${blockchains}")

    config.cmd match {
      case "server" =>
        run( config.host, config.port,config.uri,c,
          Seq(
            (WalletRegistry(store,signer,blockchains),"WalletRegistry",(r, ac) => new WalletRoutes(r)(ac,config) )
          )
        ) 
    }
  }
}
