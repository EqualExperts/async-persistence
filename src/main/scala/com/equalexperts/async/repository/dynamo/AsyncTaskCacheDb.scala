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

import scala.concurrent.{ExecutionContext, Future}

sealed case class TaskCachePersist(taskCache: TaskCache, expiry : Long, id : String)

object TaskCachePersist {
  def apply(taskCache: TaskCache, expiry : Long) : TaskCachePersist = TaskCachePersist(taskCache, expiry, id = taskCache.id)
}

trait AsyncTaskCacheDb extends AbstractDynamoOps[String, TaskCachePersist] with AsyncCache {

  override lazy val table: Table[TaskCachePersist] = {
    log.info(s"AsyncTaskCacheDb table name set to $tableName")
    Table[TaskCachePersist](tableName)
  }

  protected def generate : String = BSONObjectID.generate().stringify

  override def save(expectation: TaskCache, expire: Long)(implicit ex :ExecutionContext) : Future[TaskCache] = {
    createOrUpdate(TaskCachePersist(expectation, expire))
    ???
  }

  override def findByTaskId(id: String)(implicit ex :ExecutionContext) : Future[Option[TaskCache]] = {
    find(id)().map{
      case Right(Some(found)) => Some(found.taskCache)
      case Right(None) => None
      case Left(_) => throw new RuntimeException(s"Failed to find cached task for id : $id")
    }
  }

  override def removeById(id: String)(implicit ex :ExecutionContext) : Future[Unit] = {
    delete(id)().map {
      case Right(_) => Unit
      case Left(error : RepositoryError) => throw new RuntimeException(s"Failed to delete item with id : $id")
    }
  }

}

class AsyncTaskCacheDbRepository(val client: AmazonDynamoDBAsync, val tableName: String = "asyncTaskCache")(implicit ex :ExecutionContext) extends AsyncTaskCacheDb
