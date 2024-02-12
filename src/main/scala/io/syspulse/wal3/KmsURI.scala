package io.syspulse.wal3

/* 
kms://arn:aws:kms:eu-west-1:12345678:key/1e7f33ec-a8e4-5583-b6c4-281af46c2e55
kms://arn:aws:kms:eu-west-1:12345678
kms://http://localhost:4599
kms://
*/
case class KmsURI(uri0:String) {
  val PREFIX = "kms://"

  val regionEnv = sys.env.get("AWS_REGION")
  val accountEnv = sys.env.get("AWS_ACCOUNT")
  val hostEnv = sys.env.get("AWS_ENDPOINT")

  private val (khost:Option[String],kregion:Option[String],kaccount:Option[String],kkey:Option[String]) = parse(uri0)

  def host:Option[String] = khost
  def region:Option[String] = kregion
  def account:Option[String] = kaccount
  def key:Option[String] = kkey

  def uri:String = uri0.stripPrefix(PREFIX)

  def parse(uri:String):(Option[String],Option[String],Option[String],Option[String]) = {

    uri.stripPrefix(PREFIX).split("://|:").toList match {
      case "arn" :: "aws" :: "kms" :: region :: account :: key :: Nil  => (None,Some(region),Some(account),Some(key))
      case "arn" :: "aws" :: "kms" :: region :: account :: Nil => (None,Some(region),Some(account),None)
      case "http" :: host :: port :: Nil => (Some(s"http://${host}:${port}"),regionEnv,accountEnv,None)
      case "http" :: host :: Nil => (Some(s"http://${host}"),regionEnv,accountEnv,None)
      case "https" :: host :: port :: Nil => (Some(s"https://${host}:${port}"),regionEnv,accountEnv,None)
      case "https" :: host :: Nil => (Some(s"https://${host}"),regionEnv,accountEnv,None)
      case _ => (None,regionEnv,accountEnv,None)
    }
  }
}