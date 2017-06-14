
import akka.actor.ActorSystem
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBClientBuilder}
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.scanamo.{DynamoFormat, Scanamo, Table}
import com.gu.scanamo.error.{ConditionNotMet, DynamoReadError, InvalidPropertiesError}
import com.gu.scanamo.DynamoFormat._
import com.gu.scanamo.query.Not
import org.scalatest.{EitherValues, FreeSpec, Matchers}
import com.gu.scanamo.syntax._

import scala.collection.immutable


class ScanamoLearning extends FreeSpec with EitherValues with Matchers {

  private val amazonDynamoDBClient: AmazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
    .withEndpointConfiguration(new EndpointConfiguration("http://localhost:8000", "eu-west-1"))
    .build()

  implicit def emptyStringToNull(implicit f: DynamoFormat[String]) = new DynamoFormat[String]{
    def read(av: AttributeValue): Either[DynamoReadError, String] = {
      if (av.isNULL != null && av.isNULL.booleanValue())
        Right("")
      else
        Right(av.getS)
    }
    def write(string: String): AttributeValue = {
      if (string.isEmpty) new AttributeValue().withNULL(true) else new AttributeValue().withS(string)
    }
  }

  private val testTableName = "holidays"

  val holidayDtoTable: Table[HolidayDTO] = Table[HolidayDTO](testTableName)

  implicit val actorSystem = ActorSystem()

  "Object not found exception returns None, not an exception" in {
    val result: Option[Either[DynamoReadError, HolidayDTO]] =
      Scanamo.exec(amazonDynamoDBClient)(holidayDtoTable.get('flakeId -> "non_existing_flake_id"))

    result match {
      case Some(_) => fail("Should have gotten none since the object is not there")
      case None => // Success, the item was not found so we got a None
    }
  }

  "Optional fields" - {
    case class HolidayDTOWithoutOption(flakeId: String, name: String, optionalAttribute: String, number: Int)

    val testObjectWithoutOptionTable = Table[HolidayDTOWithoutOption](testTableName)

    "Can create data using non-optional fields and read them as optional in the future if they get updated" in {
      val objectWithoutOption = HolidayDTOWithoutOption("testObjectFlake", "name", "someOptionalValue", 123)
      val objectWithOption =
        HolidayDTO(objectWithoutOption.flakeId, objectWithoutOption.name, Some(objectWithoutOption.optionalAttribute), objectWithoutOption.number)

      val putResult = Scanamo.exec(amazonDynamoDBClient)(testObjectWithoutOptionTable.put(objectWithoutOption))
      putResult.getSdkHttpMetadata.getHttpStatusCode should be(200)

      val result: Option[Either[DynamoReadError, HolidayDTO]] =
        Scanamo.exec(amazonDynamoDBClient)(holidayDtoTable.get('flakeId -> objectWithoutOption.flakeId))

      result match {
        case Some(either) => either.right.value should be(objectWithOption)
        case None => fail("should have gotten some result")
      }
    }

    "Cannot create data with missing optionals and read them as objects that do not expect optionals" in {
      val objectWithOption = HolidayDTO("testObjectWithNoneProperty", "name", None, 123)

      val putResult = Scanamo.exec(amazonDynamoDBClient)(holidayDtoTable.put(objectWithOption))
      putResult.getSdkHttpMetadata.getHttpStatusCode should be(200)

      val result: Option[Either[DynamoReadError, HolidayDTOWithoutOption]] =
        Scanamo.exec(amazonDynamoDBClient)(testObjectWithoutOptionTable.get('flakeId -> objectWithOption.flakeId))

      result match {
        case Some(either) => either.left.value shouldBe a[InvalidPropertiesError] // missing age
        case None => fail("should have gotten some result")
      }
    }
  }

  "Empty strings" - {
    "Should be able to turn empty strings into nulls using the dynamo formats" in {
      val testEmpty = HolidayDTO("emptyString", "", Some("age"), 123)

      val putResult = Scanamo.exec(amazonDynamoDBClient)(holidayDtoTable.put(testEmpty))
      putResult.getSdkHttpMetadata.getHttpStatusCode should be(200)

      val result: Option[Either[DynamoReadError, HolidayDTO]] =
        Scanamo.exec(amazonDynamoDBClient)(holidayDtoTable.get('flakeId -> testEmpty.flakeId))

      result match {
        case Some(either) =>
          either.right.value should be(testEmpty)
        case None => fail("should have gotten some result")
      }
    }

    "Options containing empty strings are turned into None" in {
      val testEmpty = HolidayDTO("emptyStringAndEmptyName", "", Some(""), 123)

      val putResult = Scanamo.exec(amazonDynamoDBClient)(holidayDtoTable.put(testEmpty))
      putResult.getSdkHttpMetadata.getHttpStatusCode should be(200)

      val result: Option[Either[DynamoReadError, HolidayDTO]] =
        Scanamo.exec(amazonDynamoDBClient)(holidayDtoTable.get('flakeId -> testEmpty.flakeId))

      result match {
        case Some(either) =>
          either.right.value should be(HolidayDTO("emptyStringAndEmptyName", "", None, 123))
        case None => fail("should have gotten some result")
      }
    }

    "Should be able to do the same with a holidayDTO" in {
      val holiday = HolidayDTO("id", "", None, 123)

      val putResult = Scanamo.exec(amazonDynamoDBClient)(holidayDtoTable.put(holiday))
      putResult.getSdkHttpMetadata.getHttpStatusCode should be(200)

      val result: Option[Either[DynamoReadError, HolidayDTO]] =
        Scanamo.exec(amazonDynamoDBClient)(holidayDtoTable.get('flakeId -> holiday.flakeId))

      result match {
        case Some(either) =>
          either.right.value should be(holiday)
        case None => fail("should have gotten some result")
      }
    }
  }

  "Delete items" - {
    "The Scanamo client returns an InvalidPropertiesError for non existing holidays since it updates the object " +
      "and then tries to deserialise it into a HolidayDTO, which fails since the item only has id and no value" in {
      val deleteResult: Either[DynamoReadError, HolidayDTO] =
        Scanamo.exec(amazonDynamoDBClient)(holidayDtoTable.update('flakeId -> "nonExistingFlakeId", set('status -> "isDeleted")))

      deleteResult.left.value shouldBe a[InvalidPropertiesError]
      // This leaves an item in the db with Key: "nonExistingFlakeId" and no value
    }

    "Only updates the item if it exists" in {
      val result = Scanamo.exec(amazonDynamoDBClient)(
        holidayDtoTable
          .given('flakeId -> "banana")
          .update('flakeId -> "banana", set('productURL -> "http://newUrl.com"))
      )

      result.left.value shouldBe a[ConditionNotMet]
      // Returns a condition not met since we don't have a holiday with flakeId 'banana'
    }
  }

  "Query with conditions" in {
    val someId = "id"
    val otherId = "someOtherId"
    // add a holiday and mark as deleted
    Scanamo.exec(amazonDynamoDBClient)(holidayDtoTable.put(HolidayDTO(someId, "aName", None, 123)))
    Scanamo.exec(amazonDynamoDBClient)(holidayDtoTable.update('flakeId -> someId, set('status -> "isDeleted")))
    // add another holiday
    Scanamo.exec(amazonDynamoDBClient)(holidayDtoTable.put(HolidayDTO(otherId, "anotherName", None, 123)))

    // get deleted holiday by id - filtering by property
    val query: immutable.Seq[Either[DynamoReadError, HolidayDTO]] = Scanamo.exec(amazonDynamoDBClient)(
      holidayDtoTable.filter(Not('isDeleted -> "true")).query('flakeId -> someId)
    )
    query.head.right.get.flakeId should be(someId)

    // this returns nothing because the default value is blank, not "false"
    val deletedFalse: immutable.Seq[Either[DynamoReadError, HolidayDTO]] = Scanamo.exec(amazonDynamoDBClient)(
      holidayDtoTable.filter('isDeleted -> "false").scan())

    deletedFalse should be(List())

    // query with Not condition will retrieve holidays where isDeleted is not "true", so either "false" or not marked
    val notDeletedTrue: immutable.Seq[Either[DynamoReadError, HolidayDTO]] = Scanamo.exec(amazonDynamoDBClient)(
      holidayDtoTable.filter(Not('isDeleted -> "true")).scan())

    notDeletedTrue.filter(_.isRight).map(_.right.get.flakeId) should contain(otherId)
  }
}

case class HolidayDTO(
  flakeId: String,
  name: String,
  optionalAttribute: Option[String],
  number: Int
)
