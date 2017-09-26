import sbt.{Credentials, Path}
import sbt._

object BintrayCredentials {

  def apply() : Credentials = {

      val maybeCredentials = for {
        bintrayLogin <- sys.props.get("bintrayLogin")
        bintrayApiKey <- sys.props.get("bintrayApiKey")
      } yield Credentials("Bintray", "dl.bintray.com", bintrayLogin, bintrayApiKey)

    maybeCredentials.getOrElse {
      Credentials(Path.userHome / ".bintray" / ".eecredentials")
    }
  }
}
