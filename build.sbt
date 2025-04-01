ThisBuild / organization         := "com.unomic"
ThisBuild / organizationHomepage := Some(url("https://unomic.com"))
ThisBuild / scalaVersion         := "2.13.8"
ThisBuild / scalacOptions += "-Xasync"

name    := "sirjin-data-service"
version := "latest"

val AkkaVersion                     = "2.6.19"
val AkkaHttpVersion                 = "10.2.9"
val AkkaManagementVersion           = "1.1.3"
val AlpakkaKafkaVersion             = "3.0.0"
val AkkaProjectionVersion           = "1.2.4"
val AkkaPersistenceCassandraVersion = "1.0.5"

enablePlugins(AkkaGrpcPlugin)
enablePlugins(JavaAppPackaging, DockerPlugin)

libraryDependencies ++= Seq(
  // 1. Basic dependencies for a clustered application
  "com.typesafe.akka" %% "akka-stream"                 % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed"          % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed"    % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit"         % AkkaVersion % Test,
  // Akka Management powers Health Checks and Akka Cluster Bootstrapping
  "com.lightbend.akka.management" %% "akka-management"                   % AkkaManagementVersion,
  "com.typesafe.akka"             %% "akka-http"                         % AkkaHttpVersion,
  "com.typesafe.akka"             %% "akka-http-spray-json"              % AkkaHttpVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-http"      % AkkaManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion,
  "com.lightbend.akka.discovery"  %% "akka-discovery-kubernetes-api"     % AkkaManagementVersion,
  "com.typesafe.akka"             %% "akka-discovery"                    % AkkaVersion,
  // Common dependencies for logging and testing
  "com.typesafe.akka" %% "akka-slf4j"      % AkkaVersion,
  "ch.qos.logback"     % "logback-classic" % "1.2.11",
  "org.scalatest"     %% "scalatest"       % "3.2.12" % Test,
  // 2. Using gRPC and/or protobuf
  "com.typesafe.akka" %% "akka-http2-support" % AkkaHttpVersion,
  // 3. Using Akka Persistence
  "com.typesafe.akka" %% "akka-persistence-typed"     % AkkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion,
  "com.typesafe.akka" %% "akka-persistence-testkit"   % AkkaVersion % Test,
  // 4. Querying or projecting data from Akka Persistence
  "com.typesafe.akka"  %% "akka-persistence-query"       % AkkaVersion,
  "com.lightbend.akka" %% "akka-projection-eventsourced" % AkkaProjectionVersion,
  "com.lightbend.akka" %% "akka-projection-cassandra"    % AkkaProjectionVersion,
  "com.typesafe.akka"  %% "akka-stream-kafka"            % AlpakkaKafkaVersion,
  "com.lightbend.akka" %% "akka-projection-testkit"      % AkkaProjectionVersion % Test,
  /// macwire
  "com.softwaremill.macwire" %% "macros" % "2.5.7" % Provided,
  "com.softwaremill.macwire" %% "util"   % "2.5.7",
  "com.softwaremill.macwire" %% "proxy"  % "2.5.7",
  // quill
  "io.getquill" %% "quill-async-postgres" % "3.12.0",
  // "io.getquill" %% "quill-jdbc-zio"       % "3.18.0",

  // mail
  // "javax.mail"   % "javax.mail-api" % "1.6.2",
  "com.sun.mail" % "javax.mail" % "1.6.2"
)

libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-core"             % "1.0.0"
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-json-circe"       % "1.0.0"
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % "1.0.0"

libraryDependencies += "org.scala-lang.modules" %% "scala-async"   % "1.0.1"
libraryDependencies += "org.scala-lang"          % "scala-reflect" % scalaVersion.value % Provided

dockerBaseImage  := "openjdk:11"
dockerRepository := Some("unomic.registry.jetbrains.space/p/elfin-ap/containers")
