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

package com.equalexperts.async.repository.dynamo

import com.gu.scanamo.DynamoFormat
import com.gu.scanamo.query.UniqueKey

import scala.concurrent.{ExecutionContext, Future}

abstract class AbstractDynamoOps[ID, Serialized] extends DynamoOps[ID, Serialized]{

  import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
  import com.amazonaws.services.dynamodbv2.model.PutItemResult
  import com.gu.scanamo.ScanamoAsync
  import com.gu.scanamo.error.DynamoReadError._
  import com.gu.scanamo.error._
  import com.gu.scanamo.ops.ScanamoOps
  import com.gu.scanamo.syntax._
  import org.slf4j.LoggerFactory

  val log = LoggerFactory.getLogger(this.getClass)

  val client: AmazonDynamoDBAsync

  val tableName: String

  def createOrUpdate(item: Serialized)(implicit ec: ExecutionContext): Future[Either[RepositoryError, Serialized]] = {
    val putRequest: ScanamoOps[PutItemResult] = table.put(item)
    val result = asyncExec(putRequest)
    result.map(_ => Right(item)).recover(captureAndFail[Serialized]("Create failed!"))
  }

  def processList(result:Future[List[Either[DynamoReadError, Serialized]]])(implicit ec: ExecutionContext) = {
    val manipulatedResult: Future[Either[RepositoryError, Seq[Serialized]]] =
    // non-strict => strict (this will cause all the results to be pulled from DynamoDB)
      result
        .map(_.toList)
        .map(listXor => {
          val badResults = listXor.collect {
            case Left(dynamoReadError) => dynamoReadError
          }

          // Each individual result has the potential to fail and so we capture this
          badResults.foreach((dynamoError: DynamoReadError) => log.error(s"all : ${describe(dynamoError)}"))

          val goodResults = listXor.collect {
            case Right(item) => item
          }
          Right(goodResults)
        })
    manipulatedResult.recover(captureAndFail("Failed to retrieve records!"))
  }

  def delete(id: ID)(key: UniqueKey[_] = 'id -> id.toString)(implicit ec: ExecutionContext): Future[Either[RepositoryError, ID]] = {
    val deleteRequest = table.delete(key)
    val result = asyncExec(deleteRequest)
    result.map(_ => Right(id)).recover(captureAndFail(s"Delete with id: ${id.toString} failed"))
  }

  def find(id: ID)(key: UniqueKey[_] = 'id -> id.toString)(implicit ec: ExecutionContext): Future[Either[RepositoryError, Option[Serialized]]] = {
    val findRequest: ScanamoOps[Option[Either[DynamoReadError, Serialized]]] = table.get(key)
    find(findRequest)
  }

  private def find(findRequest:ScanamoOps[Option[Either[DynamoReadError, Serialized]]])(implicit ec: ExecutionContext) = {
    val dynamoResult = asyncExec(findRequest)
    val xorManipulatedResult = dynamoResult.map(
      (maybeItem: Option[Either[DynamoReadError, Serialized]]) => {

        maybeItem match {
          case None =>
            // If we get a None, it means that that item does not exist so we return a Right None
            Right[RepositoryError, Option[Serialized]](None)
          case Some(persisted) =>
            // Deserialization from (DynamoDB => Scala) has the potential to fail so we capture this
            persisted.fold(
              dynamoReadError => {
                log.info(describe(dynamoReadError))
                Left[RepositoryError, Option[Serialized]](DeserializationError)
              },
              (authority: Serialized) =>
                Right[RepositoryError, Option[Serialized]](Some(authority))
            )
        }
      }
    ).recover(captureAndFail(s"Find with id: $findRequest failed"))
    xorManipulatedResult
  }

  def all(implicit ec: ExecutionContext, dbf : DynamoFormat[Serialized]): Future[Either[RepositoryError, Seq[Serialized]]] = {
    val result: Future[List[Either[DynamoReadError, Serialized]]] = ScanamoAsync.scan[Serialized](client)(tableName)
    processList(result)
  }

  def asyncExec[A](scanamoOps: ScanamoOps[A])(implicit ec: ExecutionContext) : Future[A] = ScanamoAsync.exec(client)(scanamoOps)

  private def captureAndFail[R](msg: String): PartialFunction[Throwable, Either[RepositoryError, R]] = {
    case t: Throwable =>
      log.error(msg, t)
      Left[RepositoryError, R](ConnectionError)
  }

}

