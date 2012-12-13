name := "akka-poc"

scalaVersion := "2.10.0-RC2"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases"

retrieveManaged := true

libraryDependencies ++= Seq("com.typesafe.akka" % "akka-actor_2.10.0-RC2" % "2.1.0-RC2" withSources (),
                            "com.typesafe.akka" % "akka-remote_2.10.0-RC2" % "2.1.0-RC2" withSources (),
                            "com.typesafe.akka" % "akka-testkit_2.10.0-RC2" % "2.1.0-RC2" % "test" withSources (),
			 "org.syslog4j" % "syslog4j" % "0.9.30" withSources (),
			"commons-pool" % "commons-pool" % "1.6" withSources (),
                            "org.specs2" % "specs2_2.10.0-RC2" % "1.12.2" % "test",
                            "junit" % "junit" % "4.7" % "test",
                            "org.scala-stm" % "scala-stm_2.10.0-RC2" % "0.6" withSources ())

