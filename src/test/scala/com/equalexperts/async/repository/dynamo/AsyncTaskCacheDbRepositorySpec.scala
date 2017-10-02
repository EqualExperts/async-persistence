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
import com.equalexperts.play.asyncmvc.model.{StatusCodes, TaskCache}
import org.joda.time.{DateTime, DateTimeZone}
import support.{DynamoDBSupport, LocalDynamoDB}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils

class AsyncTaskCacheDbRepositorySpec extends UnitSpec with DynamoDBSupport {

  import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
  import scala.concurrent.ExecutionContext.Implicits.global

  def asyncTaskCacheDbRepository(tableName : String, overrridenNow : DateTime = DateTime.now.withZone(DateTimeZone.UTC))(implicit client : AmazonDynamoDBAsync) = new AsyncTaskCacheDbRepository(client, tableName) with ExpiryTime {
    override def now = overrridenNow
  }

  def tableName(testSuffix : String = "Spec") = s"asyncTaskCache-$testSuffix"

  val attributeDef = 'id -> S

  "TaskCache" should {
    import LocalDynamoDB.localAsyncClient
    import support.DynamoIntegration.withDatabase

    "create an item" in {

      val tablename = tableName("create")

      withDatabase(tablename, attributeDef) {
        tablename => client =>{

          val repo = asyncTaskCacheDbRepository(tablename)
          val taskCache = TaskCache(BSONObjectID.generate().stringify, StatusCodes.Running, None, 1233L, 1244L)

          await(repo.save(taskCache, 500L)) shouldBe taskCache

          await(repo.find(taskCache.id)()).right.get.get.taskCache shouldBe taskCache
        }
      }
    }

    "update an item" in {

      val tablename = tableName("update")

      withDatabase(tablename, attributeDef) {
        tablename => client =>{

          val now = DateTime.now.withZone(DateTimeZone.UTC)

          val repo = asyncTaskCacheDbRepository(tablename, now)
          val taskCache = TaskCache(BSONObjectID.generate().stringify, StatusCodes.Running, Some("""{"valueA":1,"valueB":2}"""), 1233L, 1244L)
          await(repo.createOrUpdate(TaskCachePersist(taskCache, 500L))).right.get.taskCache shouldBe taskCache

          val existing = await(repo.find(taskCache.id)()).right.get
          existing should not be None
          existing.get.expiry shouldBe 500L

          val update = taskCache.copy(status = StatusCodes.Complete)
          await(repo.save(update, 1000L)) shouldBe update

          val updated = await(repo.find(taskCache.id)()).right.get.get
          updated.taskCache shouldBe update
          updated.expiry shouldBe now.getMillis + 1000L
        }
      }
    }


    "find by task id" in {

      val tablename = tableName("find")

      withDatabase(tablename, attributeDef) {
        tablename => client =>{

          val repo = asyncTaskCacheDbRepository(tablename)
          val taskCache = TaskCache(BSONObjectID.generate().stringify, StatusCodes.Running, None, 1233L, 1244L)
          val persist = TaskCachePersist(taskCache,DateTimeUtils.now.plusMinutes(5).getMillis)

          await(repo.createOrUpdate(persist)).right.get shouldBe persist

          await(repo.findByTaskId(taskCache.id)).get shouldBe taskCache
        }
      }

    }

    "return None for unknown task id" in {
      val tablename = tableName("notfound")

      withDatabase(tablename, attributeDef) {
        tablename => client =>{

          val repo = asyncTaskCacheDbRepository(tablename)

          await(repo.findByTaskId("1234567890")) shouldBe None
        }
      }
    }

    "remove by task id" in {

      val tablename = tableName("remove")

      withDatabase(tablename, attributeDef) {
        tablename => client =>{

          val repo = asyncTaskCacheDbRepository(tablename)
          val taskCache = TaskCache(BSONObjectID.generate().stringify, StatusCodes.Running, None, 1233L, 1244L)
          val persist = TaskCachePersist(taskCache,DateTimeUtils.now.plusMinutes(5).getMillis)

          await(repo.createOrUpdate(persist)).right.get shouldBe persist

          await(repo.removeById(taskCache.id)) shouldBe ()

          await(repo.findByTaskId(taskCache.id)) shouldBe None
        }
      }

    }

  }
}