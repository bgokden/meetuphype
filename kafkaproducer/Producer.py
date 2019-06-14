from kafka import KafkaClient, SimpleProducer
import json,requests

# This code is taken from https://github.com/gautham20/SparkStream-for-meetup

kafka = KafkaClient('bootstrap.kafka.svc.cluster.local:9092')

producer = SimpleProducer(kafka)

r = requests.get("https://stream.meetup.com/2/rsvps",stream=True)

for line in r.iter_lines():
	producer.send_messages('meetup',line)

kafka.close()
