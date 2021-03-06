

object LocalDynamoDb {

  import com.localytics.sbt.dynamodb.DynamoDBLocalKeys._
  import sbt.Keys._
  import sbt._

  def apply(conf : Configuration) : Seq[Setting[_]] = {
    Seq(
      dynamoDBLocalDownloadDir := file("./dynamodb_local"),
      dynamoDBLocalPort := 8000,
      dynamoDBLocalSharedDB := true,
      dynamoDBLocalHeapSize := Some(1024),
      startDynamoDBLocal := startDynamoDBLocal.dependsOn(compile in conf).value,

      test in conf := (test in conf).dependsOn(startDynamoDBLocal).value,
      testOnly in conf := (testOnly in conf).dependsOn(startDynamoDBLocal).evaluated,
      testOptions in conf += dynamoDBLocalTestCleanup.value
    )
  }
}
