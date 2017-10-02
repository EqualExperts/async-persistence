/*
 * Copyright 2017 Equal Experts
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package support

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync

import scala.concurrent.{ExecutionContext, Future}

object DynamoIntegration {
  import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType

  def withDatabase[T](tableName:String, attributeDefinitions :(Symbol, ScalarAttributeType)*)(thunk: => String => AmazonDynamoDBAsync => T)(implicit client : AmazonDynamoDBAsync): Unit  = {
    LocalDynamoDB.usingTable(client)(tableName)(attributeDefinitions : _*)(thunk(tableName)(client))
  }
}


object LocalDynamoDB {

  import com.amazonaws.services.dynamodbv2._
  import com.amazonaws.services.dynamodbv2.model._

  import scala.collection.convert.decorateAsJava._

  private val arbitraryThroughputThatIsIgnoredByDynamoDBLocal = new ProvisionedThroughput(1L, 1L)

  implicit val localAsyncClient : AmazonDynamoDBAsync = LocalDynamoDB.client()

  def client(): AmazonDynamoDBAsync = {
    val creds = new BasicAWSCredentials("developer", "developers")
    AmazonDynamoDBAsyncClientBuilder.standard()
      .withCredentials(new AWSStaticCredentialsProvider(creds))
      .withEndpointConfiguration(new EndpointConfiguration("http://localhost:8000", "local"))
      .build()
  }

  def usingTable[T](client: AmazonDynamoDB)(tableName: String)(attributeDefinitions: (Symbol, ScalarAttributeType)*)(
    thunk: => T
  ): Unit = {
    withTable(client)(tableName)(attributeDefinitions: _*)(thunk)
    ()
  }

  def withTable[T](client: AmazonDynamoDB)(tableName: String)(attributeDefinitions: (Symbol, ScalarAttributeType)*)(
    thunk: => T
  ): T = {
    createTable(client)(tableName)(attributeDefinitions: _*)
    val res = try {
      thunk
    } finally {
      client.deleteTable(tableName)
      ()
    }
    res
  }

  def createTable(client: AmazonDynamoDB)(tableName: String)(attributes: (Symbol, ScalarAttributeType)*): CreateTableResult = {
    client.createTable(
      attributeDefinitions(attributes),
      tableName,
      keySchema(attributes),
      arbitraryThroughputThatIsIgnoredByDynamoDBLocal
    )
  }

  def withTableWithSecondaryIndex[T](client: AmazonDynamoDB)(tableName: String, secondaryIndexName: String)
                                    (primaryIndexAttributes: (Symbol, ScalarAttributeType)*)(secondaryIndexAttributes: (Symbol, ScalarAttributeType)*)(
                                      thunk: => T
                                    ): T = {

    val globalIndex: GlobalSecondaryIndex = new GlobalSecondaryIndex()
      .withIndexName(secondaryIndexName)
      .withKeySchema(keySchema(secondaryIndexAttributes))
      .withProvisionedThroughput(arbitraryThroughputThatIsIgnoredByDynamoDBLocal)
      .withProjection(new Projection().withProjectionType(ProjectionType.ALL))

    val table = new CreateTableRequest().withTableName(tableName)
      .withAttributeDefinitions(attributeDefinitions(
        primaryIndexAttributes.toList ++ (secondaryIndexAttributes.toList diff primaryIndexAttributes.toList)))
      .withKeySchema(keySchema(primaryIndexAttributes))
      .withProvisionedThroughput(arbitraryThroughputThatIsIgnoredByDynamoDBLocal)
      .withGlobalSecondaryIndexes(globalIndex)

    client.createTable(
      table
    )
    val res = try {
      thunk
    } finally {
      client.deleteTable(tableName)
      ()
    }
    res
  }

  private def keySchema(attributes: Seq[(Symbol, ScalarAttributeType)]) = {
    val hashKeyWithType :: rangeKeyWithType = attributes.toList
    val keySchemas = hashKeyWithType._1 -> KeyType.HASH :: rangeKeyWithType.map(_._1 -> KeyType.RANGE)
    keySchemas.map { case (symbol, keyType) => new KeySchemaElement(symbol.name, keyType) }.asJava
  }

  private def attributeDefinitions(attributes: Seq[(Symbol, ScalarAttributeType)]) = {
    attributes.map { case (symbol, attributeType) => new AttributeDefinition(symbol.name, attributeType) }.asJava
  }


  def ttl(tableName : String)(implicit client :AmazonDynamoDBAsync, ex :ExecutionContext) : Future[Unit] = {
    import com.amazonaws.services.dynamodbv2.model.TimeToLiveSpecification
    import com.amazonaws.services.dynamodbv2.model.UpdateTimeToLiveRequest

    Future{
      //table created now enabling TTL
      val req = new UpdateTimeToLiveRequest
      req.setTableName(tableName)
      val ttlSpec = new TimeToLiveSpecification
      ttlSpec.setAttributeName("start")
      ttlSpec.setEnabled(true)
      req.withTimeToLiveSpecification(ttlSpec)
      client.updateTimeToLive(req)
    }
  }
}