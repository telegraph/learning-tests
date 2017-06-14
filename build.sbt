name := "learning-tests"

version := "1.0"

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  "org.scalatest"                 %% "scalatest"                    % "3.0.3",
  "com.gu"                        %% "scanamo"                      % "0.9.3",
  "com.amazonaws"                 %  "aws-java-sdk-dynamodb"        % "1.1.126",
  "io.rest-assured"               %   "scala-support"           % "3.0.2",
  "com.github.tomakehurst"        %   "wiremock"                % "2.6.0",
  "com.amazonaws"                 %   "aws-java-sdk-sns"        % "1.11.136",
  "org.json4s"                    %%  "json4s-native"           % "3.5.2",
  "com.typesafe.akka"             %% "akka-actor" % "2.5.2"

)
