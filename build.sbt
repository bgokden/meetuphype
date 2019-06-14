import sbtassembly.MergeStrategy

name := "MeetupHype"

version := "0.1"

scalaVersion := "2.11.11"

val sparkVersion = "2.3.2"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion,
  // "org.apache.spark" %% "spark-mllib" % sparkVersion,
  "org.apache.spark" %% "spark-streaming" % sparkVersion exclude("net.jpountz.lz4", "*"),
  "org.apache.spark" %% "spark-streaming-kafka-0-10" % sparkVersion exclude("net.jpountz.lz4", "*"),
  "com.fasterxml.jackson.core" % "jackson-core" % "2.9.7",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.7",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.7",
  "org.scalatest" %% "scalatest" % "3.0.4" % Test
)



javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

mainClass := Some("com.berkgokden.MeetupHype")

mainClass in (Compile, run) := Some("com.berkgokden.MeetupHype")
mainClass in (Compile, packageBin) := Some("com.berkgokden.MeetupHype")


// Assembly file will be on fixed location with same name for easy docker builds
assemblyOutputPath in assembly := file("target/app.jar")

parallelExecution in Test := false

// uncomment to skip tests in assembly
// test in assembly := {}


/* without this explicit merge strategy code you get a lot of noise from sbt-assembly
   complaining about not being able to dedup files */
assemblyMergeStrategy in assembly := {
  case PathList("org","aopalliance", xs @ _*) => MergeStrategy.last
  case PathList("javax", "inject", xs @ _*) => MergeStrategy.last
  case PathList("javax", "servlet", xs @ _*) => MergeStrategy.last
  case PathList("javax", "activation", xs @ _*) => MergeStrategy.last
  case PathList("org", "apache", xs @ _*) => MergeStrategy.last
  case PathList("com", "google", xs @ _*) => MergeStrategy.last
  case PathList("com", "esotericsoftware", xs @ _*) => MergeStrategy.last
  case PathList("com", "codahale", xs @ _*) => MergeStrategy.last
  case PathList("com", "yammer", xs @ _*) => MergeStrategy.last
  case "about.html" => MergeStrategy.rename
  case "META-INF/ECLIPSEF.RSA" => MergeStrategy.last
  case "META-INF/mailcap" => MergeStrategy.last
  case "META-INF/mimetypes.default" => MergeStrategy.last
  case "plugin.properties" => MergeStrategy.last
  case "log4j.properties" => MergeStrategy.last
  case "overview.html" => MergeStrategy.last  // Added this for 2.1.0 I think
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

/* including scala bloats your assembly jar unnecessarily, and may interfere with
   spark runtime */
// assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)
assemblyJarName in assembly := "app.jar"

/* you need to be able to undo the "provided" annotation on the deps when running your spark
   programs locally i.e. from sbt; this bit reincludes the full classpaths in the compile and run tasks. */
fullClasspath in Runtime := (fullClasspath in (Compile, run)).value
