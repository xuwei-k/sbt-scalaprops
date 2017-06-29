import sbtrelease._
import ReleaseStateTransformations._
import com.typesafe.sbt.pgp.PgpKeys

scalapropsSettings

libraryDependencies ++= {
  if((sbtBinaryVersion in pluginCrossBuild).value.startsWith("1.0.")) {
    Nil
  } else {
    Defaults.sbtPluginExtra(
      m = "org.scala-native" % "sbt-scala-native" % nativeVersion % "provided",
      sbtV = (sbtBinaryVersion in update).value,
      scalaV = (scalaBinaryVersion in update).value
    ) :: Nil
  }
}

scalapropsVersion := "0.5.0"

def gitHash = scala.util.Try(
  sys.process.Process("git rev-parse HEAD").lines_!.head
).getOrElse("master")

ScriptedPlugin.scriptedSettings

unmanagedSourceDirectories in Compile ++= {
  if((sbtBinaryVersion in pluginCrossBuild).value.startsWith("1.0.")) {
    ((scalaSource in Compile).value.getParentFile / "scala-sbt-1.0") :: Nil
  } else {
    Nil
  }
}

sbtPlugin := true

ScriptedPlugin.scriptedBufferLog := false

ScriptedPlugin.scriptedLaunchOpts ++= sys.process.javaVmArguments.filter(
  a => Seq("-Xmx", "-Xms", "-XX", "-Dsbt.log.noformat").exists(a.startsWith)
)

ScriptedPlugin.scriptedLaunchOpts ++= Seq(
  "-Dscala-native.version=" + nativeVersion,
  "-Dplugin.version=" + version.value,
  "-Dscala-native.version=" + nativeVersion,
  "-Dscalaprops.version=" + scalapropsVersion.value
)

startYear := Some(2015)

organization := "com.github.scalaprops"

name := "sbt-scalaprops"

description := "sbt plugin for scalaprops"

homepage := Some(url("https://github.com/scalaprops/sbt-scalaprops"))

licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php"))

commands += Command.command("updateReadme")(UpdateReadme.updateReadmeTask)

pomPostProcess := { node =>
  import scala.xml._
  import scala.xml.transform._
  def stripIf(f: Node => Boolean) = new RewriteRule {
    override def transform(n: Node) =
      if (f(n)) NodeSeq.Empty else n
  }
  val stripTestScope = stripIf { n => n.label == "dependency" && (n \ "scope").text == "test" }
  new RuleTransformer(stripTestScope).transform(node)(0)
}

scalacOptions in (Compile, doc) ++= {
  val tag = if(isSnapshot.value) gitHash else { "v" + version.value }
  Seq(
    "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath,
    "-doc-source-url", s"https://github.com/scalaprops/sbt-scalaprops/tree/${tag}€{FILE_PATH}.scala"
  )
}

pomExtra :=
  <developers>
    <developer>
      <id>xuwei-k</id>
      <name>Kenji Yoshida</name>
      <url>https://github.com/xuwei-k</url>
    </developer>
  </developers>
  <scm>
    <url>git@github.com:scalaprops/sbt-scalaprops.git</url>
    <connection>scm:git:git@github.com:scalaprops/sbt-scalaprops.git</connection>
    <tag>{if(isSnapshot.value) gitHash else { "v" + version.value }}</tag>
  </scm>

scalacOptions ++= (
  "-deprecation" ::
  "-unchecked" ::
  "-Xlint" ::
  "-language:existentials" ::
  "-language:higherKinds" ::
  "-language:implicitConversions" ::
  Nil
)

def setSbtPluginCross(v: String) = "set sbtVersion in pluginCrossBuild := \"" + v + "\""
val SetSbt_0_13 = setSbtPluginCross("0.13.15")
val SetSbt_1 = setSbtPluginCross("1.0.0-M6")

def crossSbtCommand(command: String): ReleaseStep = {
  val list = List(
    SetSbt_0_13,
    command,
    SetSbt_1,
    command
  )
  releaseStepCommandAndRemaining(list.mkString(";", ";", ""))
}

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  crossSbtCommand("test"),
  releaseStepCommand(SetSbt_0_13),
  releaseStepCommand("scripted"),
  releaseStepCommand(SetSbt_1),
  releaseStepCommand("scripted test/*"),
  setReleaseVersion,
  commitReleaseVersion,
  UpdateReadme.updateReadmeProcess,
  tagRelease,
  crossSbtCommand("publishSigned"),
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  UpdateReadme.updateReadmeProcess,
  pushChanges
)

credentials ++= PartialFunction.condOpt(sys.env.get("SONATYPE_USER") -> sys.env.get("SONATYPE_PASSWORD")){
  case (Some(user), Some(password)) =>
    Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, password)
}.toList

// https://github.com/sbt/sbt/issues/3245
ScriptedPlugin.scripted := {
  val args = ScriptedPlugin.asInstanceOf[{
    def scriptedParser(f: File): complete.Parser[Seq[String]]
  }].scriptedParser(sbtTestDirectory.value).parsed
  val prereq: Unit = scriptedDependencies.value
  try {
    if((sbtVersion in pluginCrossBuild).value == "1.0.0-M6") {
      ScriptedPlugin.scriptedTests.value.asInstanceOf[{
        def run(x1: File, x2: Boolean, x3: Array[String], x4: File, x5: Array[String], x6: java.util.List[File]): Unit
      }].run(
        sbtTestDirectory.value,
        scriptedBufferLog.value,
        args.toArray,
        sbtLauncher.value,
        scriptedLaunchOpts.value.toArray,
        new java.util.ArrayList()
      )
    } else {
      ScriptedPlugin.scriptedTests.value.asInstanceOf[{
        def run(x1: File, x2: Boolean, x3: Array[String], x4: File, x5: Array[String]): Unit
      }].run(
        sbtTestDirectory.value,
        scriptedBufferLog.value,
        args.toArray,
        sbtLauncher.value,
        scriptedLaunchOpts.value.toArray
      )
    }
  } catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
}
