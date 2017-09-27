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

import com.gu.scanamo.query.UniqueKey

import scala.concurrent.ExecutionContext


sealed trait RepositoryError
case object ConnectionError extends RepositoryError
case object DeserializationError extends RepositoryError


trait DynamoOps[ID, Serialized] {

  import com.gu.scanamo.{DynamoFormat, Table}

  import scala.concurrent.Future

  val table: Table[Serialized]

  def createOrUpdate(item: Serialized)(implicit ec: ExecutionContext): Future[Either[RepositoryError, Serialized]]

  def find(id: ID)(key: UniqueKey[_])(implicit ec: ExecutionContext): Future[Either[RepositoryError, Option[Serialized]]]

  def delete(id: ID)(key: UniqueKey[_])(implicit ec: ExecutionContext): Future[Either[RepositoryError, ID]]

  def all(implicit ec: ExecutionContext, dbf : DynamoFormat[Serialized]): Future[Either[RepositoryError, Seq[Serialized]]]
}