
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.amazonaws.services.sns.model.PublishRequest
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, stubFor, urlEqualTo}
import org.json4s._
import org.json4s.native.Serialization
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FreeSpec, Matchers}
import sns.{SnsClient, SnsMessage, SnsMessageBody}

class SNSLearning extends FreeSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll {
  implicit val formats = Serialization.formats(NoTypeHints)

  val topicArn = "someTopicArn"

  val wireMockServer: WireMockServer = new WireMockServer()

  "SNS" - {
    "push some JSON to wiremock" in {
      // need a '/' at the end of '/sns-publish/' because that's the url the SNS Client hits
      val snsPublishEndpoint = "/sns-publish/"
      val exampleMessage = SnsMessageBody("flake", "type", 1234, "partner", "status")
      stubFor(post(urlEqualTo(snsPublishEndpoint))
        .willReturn(aResponse().withStatus(200).withBodyFile("SnsPublishMessageResponse.xml")))

      send(exampleMessage.toString)

      val receivedMessages = new SnsClient(snsPublishEndpoint).receivedMessages()
      receivedMessages should contain only SnsMessage("Publish", "2010-03-31", topicArn, exampleMessage)
    }
  }

  private def send(message: String) = {
    val snsClient =
      AmazonSNSClientBuilder
        .standard()
        .withEndpointConfiguration(
          new EndpointConfiguration(s"http://localhost:8080/sns-publish", "eu-west-1"))
        .build()

    snsClient.publish(new PublishRequest(topicArn, message))
  }

  override def beforeAll(): Unit = {
    wireMockServer.start()
  }

  override def afterAll() {
    wireMockServer.stop()
  }

  before {
    WireMock.reset()
  }
}
