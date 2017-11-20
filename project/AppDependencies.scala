import sbt._

object AppDependencies {

  val compile = Seq(
    "com.equalexperts" %% "play-async" % "0.1.0-9-gd6b4071" % "provided",
    "org.julienrf" %% "play-json-derived-codecs" % "3.3",
    "com.gu" %% "scanamo" % "0.9.5",
    "ch.qos.logback" % "logback-classic" % "1.1.7"
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {

    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "org.scalatest" %% "scalatest" % "3.0.3" % scope,
        "uk.gov.hmrc" %% "hmrctest" % "2.3.0" % scope
      )
    }.test
  }
  def apply() = compile ++ Test()
}


