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

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient
import com.equalexperts.async.repository.dynamo.RepositoryError
import com.gu.scanamo.{DynamoFormat, ScanamoAsync}
import com.gu.scanamo.error.DynamoReadError

import scala.concurrent.{ExecutionContext, Future}

trait DynamoDBSupport {

  def scanTable[Item](client:AmazonDynamoDBAsyncClient, tableName:String)(implicit ec : ExecutionContext, dynamoFormat: DynamoFormat[Item]): Future[Either[RepositoryError, Seq[Item]]] = {
    val result: Future[List[Either[DynamoReadError, Item]]] = ScanamoAsync.scan[Item](client)(tableName)
    processList(result)
  }

  def processList[Item](result:Future[List[Either[DynamoReadError, Item]]])(implicit ec : ExecutionContext) : Future[Either[RepositoryError, Seq[Item]]]= {
    val manipulatedResult: Future[Either[RepositoryError, Seq[Item]]] =
      result
        .map(_.toList)
        .map(listXor => {
          val badResults = listXor.collect {
            case Left(dynamoReadError) => dynamoReadError
          }
          val goodResults = listXor.collect {
            case Right(authority) => authority
          }
          Right(goodResults)
        })
    manipulatedResult
  }

}
