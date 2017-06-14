# SNS
Tests to prove we can use WireMock to act like SNS

# Scanamo: explore how scanamo treats some specific cases around empty strings and optional fields, objects not found and queries
For the Scanamo Learning test you'll need to either use your own Dynamo or start up an instance locally.
To run Dynamo locally:
- Download from: `http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.html#DynamoDBLocal.DownloadingAndRunning`
- Unzip and run with: `java -Djava.library.path=./DynamoDBLocal_lib -jar DynamoDBLocal.jar -sharedDb`
- Create table used in tests with: `aws dynamodb create-table --table-name holidays --attribute-definitions AttributeName=flakeId,AttributeType=S --key-schema AttributeName=flakeId,KeyType=HASH --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 --endpoint-url http://localhost:8000`
- Check that the table was created with: `aws dynamodb list-tables --endpoint-url http://localhost:8000`
- Run the tests
