# async-persistence

**** NOTE : THIS IS UNDER ACTIVE DEVELOPMENT ****

[ ![Download](https://api.bintray.com/packages/equalexperts/open-source-release-candidates/async-persistence/images/download.svg) ](https://bintray.com/equalexperts/open-source-release-candidates/async-persistence/_latestVersion)
[![CircleCI](https://circleci.com/gh/EqualExperts/async-persistence.svg?style=svg)](https://circleci.com/gh/EqualExperts/async-persistence)

Library to provide persistence to DynamoDB to support asynchronous functions from [play-async](https://github.com/EqualExperts/play-async)

## Prerequisits

* [SBT](http://www.scala-sbt.org/)
* Install [AWS CLI](https://aws.amazon.com/cli/)
    * [AWS installation instructions](http://docs.aws.amazon.com/cli/latest/userguide/installing.html)
        * Mac users with brew run ```brew install awscli```
    * [Configure AWS CLI](http://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html)
        * Once the CLI is installed run `aws config` and if you don't have an AWS account just put any values in when prompted

## DynamoDB

See script `configure_dynamodb.sh` which is used to create the `asyncTaskCache` table.

If running tests within an IDE the DynamoDB will need to be manually started as the SBT task will not be invoked.
The easiest way to do this is from the `sbt` shell `startDynamodbLocal`. 
Don't forget that you will need to stop it manually too, `stopDynamodbLocal`.

For more information on the DynamoDB SBT plugin please see [sbt-dynamodb](https://github.com/localytics/sbt-dynamodb/blob/master/README.md)


### Installing

Include the following dependency in your SBT build

* Release candidate versions

[ ![Download](https://api.bintray.com/packages/equalexperts/open-source-release-candidates/async-persistence/images/download.svg) ](https://bintray.com/equalexperts/open-source-release-candidates/async-persistence/_latestVersion)

```scala
resolvers += Resolver.bintrayRepo("equalexperts", "open-source-release-candidates")

libraryDependencies += "com.equalexperts" %% "async-persistence" % "[INSERT-VERSION]"
```

* Released versions

TBC

```scala
resolvers += Resolver.bintrayRepo("equalexperts", "open-source")

libraryDependencies += "com.equalexperts" %% "async-persistence" % "[INSERT-VERSION]"
```

### Building with Docker

To do this you will need to install [Docker](https://www.docker.com/community-edition)

`docker build -t async-persistence:latest .`

### Publishing with Docker

`docker run -v ~/.ivy2:/root/.ivy2 -t async-persistence:latest`


## Contributors 

This based off a forked from [/hmrc/microservice-async](https://github.com/hmrc/microservice-async)


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
