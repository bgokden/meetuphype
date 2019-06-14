package com.berkgokden

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.streaming._
import org.apache.spark.SparkConf
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import scala.util.Try


import Operations._
/**
  * MeetupHype calculates trending topics using Meetup api.
  * It requies a running kafka instance
  * Recommended Architecture is as below:
  * Producer => Kafka => MeetupHype Spark Application
  */
object MeetupHype {
  def main(args: Array[String]) {
    if (args.length < 5) {
      System.err.println(s"""
                            |Usage: MeetupHype <brokers> <topics> <numberOfTrending> <lastDays> <decayFactor>
                            |  <brokers> is a list of one or more Kafka brokers
                            |  <groupId> is a consumer group name to consume from topics
                            |  <topics> is a list of one or more kafka topics to consume from
                            |  <numberOfTrending> is the number of trending topics to show
                            |  <lastDays> is the number of last days to use while calculating
                            |  <decayFactor> is the factor to decrease effect of older rsvps
                            |
        """.stripMargin)
      System.exit(1)
    }

    StreamingUtil.setStreamingLogLevels()

    val Array(brokers, groupId, topics, numberOfTrending, lastDays, decayFactorStr) = args

    val numberOfTrendingTopics = Try(numberOfTrending.toInt).getOrElse(10)

    val numberOfLastDays = Try(lastDays.toInt).getOrElse(1)

    val decayFactor = Try(decayFactorStr.toDouble).getOrElse(1D)

    // Create context with 2 second batch interval
    val sparkConf = new SparkConf().setAppName("MeetupHype")
    val ssc = new StreamingContext(sparkConf, Minutes(1))
    // In production checkpointDirectory should be hdfs compatible file system like s3 or google data storage
    val checkpointDirectory = "/tmp"
    ssc.checkpoint(checkpointDirectory)
    ssc.remember(Minutes(30)) // Remember last 30 minutes

    // Create direct kafka stream with brokers and topics
    val topicsSet = topics.split(",").toSet
    val kafkaParams = Map[String, Object](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> brokers,
      ConsumerConfig.GROUP_ID_CONFIG -> groupId,
      ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer],
      ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer])
    val messages = KafkaUtils.createDirectStream[String, String](
      ssc,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[String, String](topicsSet, kafkaParams))

    // Get the message and filter only interested rsvps
    val rsvps = messages
      .map(parseToJsonSafely)
      .filter(isInterested)

    // Convert Rsvps into topic counts with id and time bucket
    // and reduce repeated ids into one
    // Keeping sliding window at 1 minute may cause results to be used multiple times but it doesn't change the result
    val topicMap = rsvps.flatMap(rsvpToTopicCountPairs)
      .map(x => (x.id, x))
      .reduceByKeyAndWindow(reduceTopicCountWithId, Minutes(10), Minutes(1))

    // Change key from id to topic name
    val listOfCountMap = topicMap.map{case (a, b) => (b.topicName, TopicCount(b.count, b.time))}

    // Initial state RDD for mapWithState operation, this helps in debugging so keep hello world
    val initialRDD = ssc.sparkContext.parallelize(List( ("hello", TopicState(Map())), ("world", TopicState(Map()))))

    // States are required not to repeat older calculations
    // Reduce operation is done while updating states with stream
    val stateDstream = listOfCountMap.mapWithState(
      StateSpec.function(mappingFunc(numberOfLastDays))
        .timeout(Minutes(70)) // This should be more than an hour due to state update design
        .initialState(initialRDD))

    // This operations allows getting top treding topics using the current state snapshot
    val trendingTopics = stateDstream.stateSnapshots()
      .map{ case (key, state) => List(TopicTrend(key, getTrendOfState(state, getCurrentTimeBucket(), decayFactor))) }
      .reduce(reduceListToBestN(numberOfTrendingTopics) )
      .map(_.map(_.name).mkString("\n"))

    // In a production environmnet it is better to send the result to a rest endpoint or write to a hdfs file system
    trendingTopics.print()

    // Start the computation
    ssc.start()
    ssc.awaitTermination()
  }

}
