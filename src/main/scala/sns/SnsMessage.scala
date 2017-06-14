package sns

import java.lang.Math._
import java.net.URLDecoder

import org.json4s._
import org.json4s.Extraction.decompose
import org.json4s.native.JsonMethods._

case class SnsMessageBody(flakeId:String, `type`:String, timestamp:Long, partner:String, status:String){
  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case other: SnsMessageBody =>
        this.flakeId == other.flakeId &&
        this.`type`  == other.`type`  &&
        this.partner == other.partner &&
        this.status  == other.status  &&
        abs(this.timestamp - other.timestamp) <= 60000
      case _ => false
    }
  }

  override def toString: String = {
    implicit val formats:Formats = DefaultFormats

    compact(render(decompose(this)))
  }
}

object SnsMessageBody {
  def apply(message:String):SnsMessageBody = {
    implicit val formats = DefaultFormats

    parse(message).extract[SnsMessageBody]
  }
}

case class SnsMessage(action: String, version: String, topicArn: String, message: SnsMessageBody) {
  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case other: SnsMessage =>
        this.action   == other.action   &&
        this.version  == other.version  &&
        this.topicArn == other.topicArn &&
        this.message  == other.message
      case _ => false
    }
  }
}

object SnsMessage {

  def apply(snsMessageBodyAsString: String): SnsMessage = {
    val requestSections = snsMessageBodyAsString.split("&")
      .map( keyValue => {
        val (key :: value :: _) = keyValue.split("=").toList
        (key, urlDecoded(value))
      })
      .toMap

    fromMap(requestSections)
  }

  private def fromMap(snsMessageMap: Map[String, String]): SnsMessage = {
    SnsMessage(
      action   = snsMessageMap("Action"),
      version  = snsMessageMap("Version"),
      topicArn = snsMessageMap("TopicArn"),
      message  = SnsMessageBody(snsMessageMap("Message"))
    )
  }

  private def urlDecoded(str: String): String = URLDecoder.decode(str, "utf8")
}
