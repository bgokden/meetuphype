package com.berkgokden

import java.io.InputStream

import org.scalatest.FunSuite
import Operations._

import scala.io.Source
import MarshallableImplicits._
import org.apache.kafka.clients.consumer.ConsumerRecord


class OperationsTest extends FunSuite {

  test("Trend of a state should return the right calculated value") {
    val currentTimeBucket = 1538428740000L / AN_HOUR_IN_MILLIS
    val state = TopicState(Map(currentTimeBucket-2 ->15L, currentTimeBucket-1 ->6L, currentTimeBucket -> 3L))
    val result = getTrendOfState(state, currentTimeBucket, 1)  // 5 + 3 + 3 = 11
    assert(result == 11)
    val result2 = getTrendOfState(state, currentTimeBucket, 2) // 1.666, 1,5, 3.0
    assert(result2 == 6)
  }

  test("read objects") {
    val stream : InputStream = getClass.getResourceAsStream("/meetup.json")
    val lines = Source.fromInputStream(stream, "UTF-8").getLines
    val dummyRsvp = Rsvp()
    val rsvps = lines.map(x => new ConsumerRecord[String, String]("", 1, 0L, "", x) ).map(parseToJsonSafely).toList
    assert(rsvps.size == 1542) // Expected number of object same as number of lines
    val errorCount = rsvps.count(_ == dummyRsvp)
    assert(errorCount == 1) // There is an uncompleted line so it is not serialized
    val interesedCount = rsvps.count(isInterested)
    assert(interesedCount == 1127)
  }

  test("deserialize json to case class") {
    // First line of meetup.json
    val jsonString = "{\"venue\":{\"venue_name\":\"Datong High School\",\"lon\":0,\"lat\":0,\"venue_id\":23779799},\"visibility\":\"public\",\"response\":\"no\",\"guests\":0,\"member\":{\"member_id\":120119272,\"photo\":\"http:\\/\\/photos3.meetupstatic.com\\/photos\\/member\\/b\\/2\\/b\\/c\\/thumb_262125756.jpeg\",\"member_name\":\"Allen Wang\"},\"rsvp_id\":1658733801,\"mtime\":1489925470960,\"event\":{\"event_name\":\"Play Intermediate Volleyball\",\"event_id\":\"jkpwmlywgbmb\",\"time\":1491613200000,\"event_url\":\"https:\\/\\/www.meetup.com\\/Taipei-Sports-and-Social-Club\\/events\\/236786445\\/\"},\"group\":{\"group_topics\":[{\"urlkey\":\"fitness\",\"topic_name\":\"Fitness\"},{\"urlkey\":\"mountain-biking\",\"topic_name\":\"Mountain Biking\"},{\"urlkey\":\"sports\",\"topic_name\":\"Sports and Recreation\"},{\"urlkey\":\"outdoors\",\"topic_name\":\"Outdoors\"},{\"urlkey\":\"fun-times\",\"topic_name\":\"Fun Times\"},{\"urlkey\":\"winter-and-summer-sports\",\"topic_name\":\"Winter and Summer Sports\"},{\"urlkey\":\"adventure\",\"topic_name\":\"Adventure\"},{\"urlkey\":\"water-sports\",\"topic_name\":\"Water Sports\"},{\"urlkey\":\"sports-and-socials\",\"topic_name\":\"Sports and Socials\"},{\"urlkey\":\"hiking\",\"topic_name\":\"Hiking\"},{\"urlkey\":\"excercise\",\"topic_name\":\"Exercise\"},{\"urlkey\":\"recreational-sports\",\"topic_name\":\"Recreational Sports\"}],\"group_city\":\"Taipei\",\"group_country\":\"tw\",\"group_id\":16585312,\"group_name\":\"Taipei Sports and Social Club\",\"group_lon\":121.45,\"group_urlname\":\"Taipei-Sports-and-Social-Club\",\"group_lat\":25.02}}"
    val caseClassOfRsvp = jsonString.fromJson[Rsvp]()
    assert(caseClassOfRsvp.rsvp_id == 1658733801L)
    assert(caseClassOfRsvp.mtime == 1489925470960L)
    assert(caseClassOfRsvp.event.event_name == "Play Intermediate Volleyball")
    assert(caseClassOfRsvp.response == "no")
    assert(caseClassOfRsvp.group.group_topics.length == 12)
    assert(caseClassOfRsvp.group.group_topics.head.topic_name == "Fitness")
  }


}
