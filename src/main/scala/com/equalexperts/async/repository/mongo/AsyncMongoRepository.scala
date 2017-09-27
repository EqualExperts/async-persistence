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
import com.equalexperts.play.asyncmvc.repository.AsyncCache
import play.api.libs.json._
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.bson._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{AtomicUpdate, BSONBuilderHelpers, DatabaseUpdate, ReactiveRepository}
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class TaskCacheMongoPersist(id: BSONObjectID, task:TaskCache){
  def toTaskCache : TaskCache = task
}

object TaskCacheMongoPersist {

  val mongoFormats: Format[TaskCacheMongoPersist] = ReactiveMongoFormats.mongoEntity(
  {
    implicit val oidFormat = ReactiveMongoFormats.objectIdFormats
    Format(Json.reads[TaskCacheMongoPersist], Json.writes[TaskCacheMongoPersist])
  })
}



trait AsyncRepository extends AsyncCache

object AsyncRepository extends MongoDbConnection {
  lazy val mongo = new AsyncMongoRepository
  def apply(): AsyncRepository = mongo
}

class AsyncMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[TaskCacheMongoPersist, BSONObjectID]("asynctaskcache", mongo, TaskCacheMongoPersist.mongoFormats, ReactiveMongoFormats.objectIdFormats)
          with AtomicUpdate[TaskCacheMongoPersist]
          with AsyncRepository
          with BSONBuilderHelpers {

  override def ensureIndexes(implicit ec: ExecutionContext): Future[scala.Seq[Boolean]] = {
    // set to zero for per document TTL using 'expiry' attribute to define the actual expiry time.
    val expireAfterSeconds = 0

    Future.sequence(
      Seq(
        collection.indexesManager.ensure(
          Index(Seq("task.id" -> IndexType.Ascending), name = Some("task.id"), unique = true)),
        collection.indexesManager.ensure(
          Index(
            key = Seq("expiry" -> IndexType.Ascending),
            options = BSONDocument("expireAfterSeconds" -> expireAfterSeconds)))
      )
    )
  }

  override def isInsertion(suppliedId: BSONObjectID, returned: TaskCacheMongoPersist): Boolean =
    suppliedId.equals(returned.id)

  private def modifierForInsert(task: TaskCache, expire: Long): BSONDocument = {
    val mandatory = BSONDocument(
      "$setOnInsert" -> BSONDocument("task.id" -> task.id),
      "$setOnInsert" -> BSONDocument("task.start" -> task.start),
      "$setOnInsert" -> BSONDocument("expiry" -> BSONDateTime(DateTimeUtils.now.getMillis + expire)),
      "$set" -> BSONDocument("task.complete" -> task.complete),
      "$set" -> BSONDocument("task.status" -> task.status)
    )

    val optional = task.jsonResponse.fold(BSONDocument.empty) { response => BSONDocument("$set" -> BSONDocument("task.jsonResponse" -> response)) }
    mandatory ++ optional
  }

  protected def findById(id: String) = BSONDocument("task.id" -> BSONString(id))

  override def findByTaskId(id: String)(implicit ex :ExecutionContext): Future[Option[TaskCache]] = {
    collection.find(findById(id)).one[TaskCacheMongoPersist](ReadPreference.primaryPreferred).map{
      case Some(t : TaskCacheMongoPersist) => Some(t.toTaskCache)
      case _ => None
    }
  }

  override def removeById(id: String)(implicit ex :ExecutionContext): Future[Unit] = {
    import reactivemongo.bson.BSONDocument
    collection.remove(BSONDocument("task.id" -> id)).map(_ => {})
  }

  override def save(task: TaskCache, expire: Long)(implicit ex :ExecutionContext): Future[TaskCache] = {
    atomicUpsert(findById(task.id), modifierForInsert(task, expire)).map{
        case update : DatabaseUpdate[TaskCacheMongoPersist] => update.updateType.savedValue.toTaskCache
        case _ => throw new RuntimeException("Failed to save task")
    }
  }

}

