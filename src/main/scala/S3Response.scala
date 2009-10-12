package org.s3q
import scala.xml._
import scala.xml.parsing._
import Environment._
import net.lag.configgy.Configgy
import net.lag.logging.Logger

case class S3Exception(val status: Int, val response:String) extends Exception {
  override def toString = {"error code " + status + ": " + response}
}

class S3Response(exchange: S3Exchange) {
  private val log = Logger.get

  lazy val whenFinished = {
    exchange.get
  }

  lazy val data = getData

  private def getData: Option[String] = {
    // Possibly we should not retry for other response types as well.
    if(status != 200){
      if(status == 404){
        log.debug("Received 404 response")
        return None
      }
      if(!request.isRetriable){
        log.error("Received %s error response: Not Retrying", status)
        throw(S3Exception(status, whenFinished.getResponseContent))
      } else {
        log.error("Received %s error response: Retrying", status)
        request.incrementAttempts
        return client.execute(request).data
      }
    }
    log.debug("Received %s successful response", status)
    Some(whenFinished.getResponseContent)
  }

  def status: Int = {
    whenFinished.getResponseStatus
  }

  def request: S3Request = exchange.request

  def client: S3Client = exchange.client

  def verify = { }
}

class S3PutResponse(exchange: S3Exchange) extends S3Response(exchange: S3Exchange) {
  override def verify = {
    data
  }
}

class S3ListResponse(exchange: S3Exchange) extends S3Response(exchange: S3Exchange) {
  lazy val doc = { data match {
    case Some(string) => XML.loadString(string)
    case None => null
    }
  }

  def items: Seq[String] = {
    (doc \\ "Contents" \\ "Key").map { _.text }
  }

  def isTruncated = {
    (doc \\ "IsTruncated").text == "true"
  }

}
