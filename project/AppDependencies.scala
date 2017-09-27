import sbt._

object AppDependencies {

  val compile = Seq(
    "com.equalexperts" %% "play-async" % "0.1.0-2-ge5bcb84" % "provided",
    "uk.gov.hmrc" %% "play-reactivemongo" % "5.2.0"
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {

    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "org.scalatest" %% "scalatest" % "3.0.3" % scope,
        "uk.gov.hmrc" %% "hmrctest" % "2.3.0" % scope,
        "uk.gov.hmrc" %% "reactivemongo-test" % "1.6.0" % scope
      )
    }.test
  }
  def apply() = compile ++ Test()
}


