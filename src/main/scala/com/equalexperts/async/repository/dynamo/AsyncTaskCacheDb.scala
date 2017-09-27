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

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.equalexperts.play.asyncmvc.model.TaskCache
import com.equalexperts.play.asyncmvc.repository.AsyncCache
import com.gu.scanamo.Table

import scala.concurrent.ExecutionContext


trait AsyncTaskCacheDb extends AbstractDynamoOps[String, TaskCache] with AsyncCache {

  import com.gu.scanamo.syntax._

  override lazy val table: Table[TaskCache] = {
    log.info(s"AsyncTaskCacheDb table name set to $tableName")
    Table[TaskCache](tableName)
  }

  protected def generate : String = BSONObjectID.generate().stringify

  override def save(expectation: TaskCache, expire: Long)(implicit ex :ExecutionContext) = {
    createOrUpdate(expectation)
    ???
  }

  override def findByTaskId(id: String)(implicit ex :ExecutionContext) = {
    find(id)()
    ???
  }

  override def removeById(id: String)(implicit ex :ExecutionContext) = {
    delete(id)()
    ???
  }
}

class AsyncTaskCacheDbRepository(val client: AmazonDynamoDBAsync, val tableName: String = "asyncTaskCache") extends AsyncTaskCacheDb
