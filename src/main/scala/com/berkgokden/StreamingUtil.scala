package com.berkgokden

import org.apache.log4j.{Level, Logger}
import org.apache.spark.internal.Logging

/** Utility functions for Spark Streaming */
object StreamingUtil extends Logging {

  /** Set reasonable logging levels for streaming if the user has not configured log4j. */
  def setStreamingLogLevels() {
    val log4jInitialized = Logger.getRootLogger.getAllAppenders.hasMoreElements
    if (!log4jInitialized) {
      // We first log something to initialize Spark's default logging, then we override the
      // logging level.
      logInfo("Setting log level to [WARN] for streaming app." +
        " To override add a custom log4j.properties to the classpath.")
      // As an experience this doesn't work when remote submit is used
      // use --conf instead eg.:
      // --conf spark.driver.extraJavaOptions=-Dlog4j.configuration=file:/opt/spark/conf/log4j.properties
      // --conf spark.executor.extraJavaOptions=-Dlog4j.configuration=file:/opt/spark/conf/log4j.properties
      Logger.getRootLogger.setLevel(Level.WARN)
    }
  }
}