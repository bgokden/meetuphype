package com.berkgokden

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.spark.streaming.State

import scala.util.Try
import MarshallableImplicits._


case class TopicCountWithId(topicName: String, count: Long, id: Long, time: Long)
case class TopicCount(count: Long, time: Long)

case class Topic(urlkey: String, topic_name: String)
case class Group(group_topics: List[Topic])
case class Event(event_name: String = "")

case class Rsvp(
                 response: String = "no",
                 rsvp_id: Long = 0,
                 mtime: Long = 0,
                 guests: Long = 0,
                 group: Group = Group(List()),
                 event: Event = Event("")
               )

case class TopicState(counts: Map[Long, Long])
case class TopicTrend(name: String, trend: Long)

/**
  * Operations and case classes that are used in the app are grouped here
  * An object is needed to avoid serialization issues.
  * Although it is an object, it is not designed as a singleton.
  * It is easier to test single functions one by one.
  */
object Operations {

  val AN_HOUR_IN_MILLIS = 3600000L
  def getCurrentTimeBucket() = System.currentTimeMillis() / AN_HOUR_IN_MILLIS

  //
  // This will give a DStream made of state (which is the cumulative count of the words)
  /**
    * Update the cumulative count using mapWithState
    * This will give a DStream made of state which has a map of the cumulative counts of the rsvps
    *
    * @param numberOfDays number of days to keep in the state
    * @return output but it is not used in this app
    */
  def mappingFunc(numberOfDays: Long) = (topic: String, one: Option[TopicCount], state: State[TopicState]) => {
    // val sum = one.getOrElse(0L) +
    val currentState =  state.getOption.getOrElse(TopicState(counts = Map()))
    val buckedId = one.getOrElse(TopicCount(0L,0L)).time
    val count = one.getOrElse(TopicCount(0L,0L)).count
    val timeLimit = getCurrentTimeBucket - (numberOfDays*24)
    val newCountsMap = currentState.counts.filterKeys(time => time >= timeLimit )
    val newState = currentState.copy(counts = newCountsMap ++ Map(buckedId -> (currentState.counts.getOrElse(buckedId, 0L) + count)))
    val output = (topic, newState.counts.map{case (k, v) => v}.sum ) // This is actually not useful
    Try( state.update(newState) ) // There is an exception when state is timing out while updating.
    output
  }

  /**
    * Wrap a topic inside a List while mapping,
    * This is required in next reduce operation
    * @param topicCountWithId
    * @return List of TopicCount
    */
  def wrapAsList(topicCountWithId: TopicCountWithId) = List(TopicCount(topicCountWithId.count, topicCountWithId.time))

  /**
    * Parse String to Json object of Rsvp
    * Sometimes parsing fails to parse some fields due to wrong closing quotation or similar
    * If the json is not parsable, it is just skipped by returning a dummy object.
    * Jackson does everything it can to recover these issues.
    * See JsonUtil for configuration
    * For unknown fields, Jackson is configured to not to return an error.
    * Most fields are not neeeded. See Rsvp case class for default values
    * See https://www.meetup.com/meetup_api/docs/stream/2/rsvps/ for documentation for rsvp json format
    * @return parsed rsvp or a dummy object with response as no
    */
  def parseToJsonSafely = (message: ConsumerRecord[String, String]) =>
    Try(message.value.fromJson[Rsvp]()).getOrElse(Rsvp())

  /**
    * checks if an rsvp is considered as interesed
    * @return true if response is yes
    */
  def isInterested = (rsvp: Rsvp) => Try(rsvp.response.trim.compareToIgnoreCase("yes") == 0).getOrElse(false)

  /**
    * Extract Topic Counts
    * Event name is includes as a topic
    * Count is calculated including guests
    * Rsvp id is preserved for future usage
    * @return List of Topic Counts with id
    */
  def rsvpToTopicCountPairs = (rsvp: Rsvp) => {
    val count:Long = 1 + rsvp.guests
    val buckedTime = rsvp.mtime / 3600000L // Divide by 1 hour as milliseconds
    rsvp.group.group_topics.map(topic => TopicCountWithId(topic.topic_name.trim, count, rsvp.rsvp_id, buckedTime)) ++
      List(TopicCountWithId(rsvp.event.event_name.trim, count, rsvp.rsvp_id, buckedTime))
  }

  /**
    * Reduce operation for TopicCountWithId
    * This operation is needed since maybe same rsvp is read multiple times
    * from the rest stream
    * @return merged topic count with id
    */
  def reduceTopicCountWithId = (a:TopicCountWithId, b:TopicCountWithId) => {
    if (a.id == b.id) {
      a
    } else {
      // This is not expected since this operation is used in a reduce by id operation
      // but accidents happen
      TopicCountWithId(a.topicName, a.count+b.count, a.id, a.time)
    }
  }

  /**
    * reduce operation to get best N topics ordered by count
    * This operation runs much faster than ordering then getting first N
    * @param numberOfTrendingTopics
    * @return reduced best N topics ordered by count
    */
  def reduceListToBestN(numberOfTrendingTopics: Int) =
    (a:List[TopicTrend], b:List[TopicTrend]) =>
      (a ++ b).sortWith((c, d) => c.trend > d.trend).take(numberOfTrendingTopics)


  /**
    * Decay Coefficient is calculated by applying exponentially decreasing depeding on time difference
    * @param timeBucket
    * @param currentTimeBucket
    * @param decayFactor
    * @return coefficient to multiply with count
    */
  def decayCoefficient(timeBucket: Long, currentTimeBucket: Long, decayFactor: Double):Double = {
    // currentTimeBucket > timeBucket
    val decimator = Math.pow(Math.abs(currentTimeBucket - timeBucket)+1, decayFactor)
    if ( decimator == 0 ) { // decay factor can not be 0
      1D
    } else {
      1D / decimator
    }
  }

  /**
    * This is a custom trend function calculated by using time
    * @param state
    * @param currentTimeBucket
    * @return
    */
  def getTrendOfState(state: TopicState, currentTimeBucket: Long, decayFactor: Double): Long = {
    Try(state.counts.map{case (k,v) => v*decayCoefficient(k, currentTimeBucket, decayFactor)}.sum.toLong)
      .getOrElse(0L)
  }

}
