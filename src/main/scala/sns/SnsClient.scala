package sns

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._

import scala.collection.JavaConverters._

class SnsClient(urlPattern: String) {

  def receivedMessages(): List[SnsMessage] = {
    val matchingRequests = WireMock.findAll(postRequestedFor(urlPathEqualTo(urlPattern))).asScala.toList

    matchingRequests.map(request => SnsMessage(request.getBodyAsString))
  }
}
