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

package com.equalexperts.async.repository.mongo

import com.equalexperts.play.asyncmvc.model.TaskCache
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Span}
import org.scalatest.{BeforeAndAfterEach, LoneElement}
import reactivemongo.bson.BSONObjectID
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.mongo._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global


class AsyncMongoRepositorySpec extends UnitSpec with
                                                 MongoSpecSupport with
                                                 BeforeAndAfterEach with
                                                 ScalaFutures with
                                                 LoneElement with
                                                 Eventually {

  private val expireTime = 2000
  private val repository: AsyncMongoRepository = new AsyncMongoRepository

  trait Setup {
    val authId = "some-auth-id"
    val testToken = "token"
    val id = "someId"
    val task = TaskCache("someId", 1, Some("""{"value":1}"""), 1, 1)
    val taskUpdate = TaskCache("someId", 4, Some("""{"valueA":1,"valueB":2}"""), 1, 1)
    val id2 = "someId2"
    val task2 = TaskCache("someId2", 2, Some("""{"value":2}"""), 2, 2)
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  "Validating index's " should {

    "not be able to insert duplicate data entries" in new Setup {
      val resp: TaskCache = await(repository.save(task, expireTime))

      def toTaskCacheMongoPersist(t : TaskCache) : TaskCacheMongoPersist = TaskCacheMongoPersist(BSONObjectID.generate, t)

      a[DatabaseException] should be thrownBy await(repository.insert(toTaskCacheMongoPersist(resp)))
      a[DatabaseException] should be thrownBy await(repository.insert(toTaskCacheMongoPersist(resp).copy(id = BSONObjectID.generate)))
      a[DatabaseException] should be thrownBy await(repository.insert(toTaskCacheMongoPersist(resp).copy(id = BSONObjectID.generate)))
    }
  }

  "repository" should {

    "create multiple records with different id's" in new Setup {
      val result = await(repository.save(task, expireTime))
//      result shouldBe an[Saved[_]]
      result shouldBe task
      result.id shouldBe id

      await(repository.findByTaskId(id)).get shouldBe task

      val resultSecond = await(repository.save(task2, expireTime))

      resultSecond shouldBe task2
      resultSecond.id shouldBe id2

      await(repository.findByTaskId(id2)).get shouldBe task2
    }

    "update an existing record" in new Setup {
      val result = await(repository.save(task, expireTime))

      result shouldBe task
      result.id shouldBe id

      await(repository.findByTaskId(id)).get shouldBe task
      val result2 = await(repository.save(task.copy(status=4,jsonResponse=taskUpdate.jsonResponse), expireTime))

      await(repository.findByTaskId(id)).get shouldBe taskUpdate
    }

    "remove the record" in new Setup {
      val result = await(repository.save(task, expireTime))
      await(repository.findByTaskId(id)).get shouldBe task

      await(repository.removeById(id))

      await(repository.findByTaskId(id)) shouldBe None
    }

    "not remove a record when an invalid Id is supplied" in new Setup {
      val result = await(repository.save(task, expireTime))
      await(repository.findByTaskId(id)).get shouldBe task

      await(repository.removeById("not found"))

      await(repository.findByTaskId(id)).get shouldBe task
    }

    "remove the record when the expiration threshold is reached" in new Setup {
      val result = await(repository.save(task, 1))
      await(repository.findByTaskId(id)).get shouldBe task

      eventually(Timeout(Span(60000, Millis))) {
        await(repository.findByTaskId(id)) should equal(None)
      }
    }

    "not find an existing record with an invalid search key" in new Setup {
      val result = await(repository.save(task, expireTime))

      val findResult = await(repository.findByTaskId("unknown"))
      findResult shouldBe None
    }
  }
}
