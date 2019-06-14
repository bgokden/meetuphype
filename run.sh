#!/usr/bin/env bash

bin/spark-submit \
    --master k8s://https://35.224.89.7:443 \
    --deploy-mode cluster \
    --name spark-meetup \
    --driver-memory 3g \
    --class com.berkgokden.MeetupHype \
    --conf spark.executor.instances=5 \
    --conf spark.kubernetes.namespace=apps \
    --conf spark.kubernetes.container.image=berkgokden/spark:0.14 \
    --conf spark.driver.extraJavaOptions=-Dlog4j.configuration=file:/opt/spark/conf/log4j.properties \
    --conf spark.executor.extraJavaOptions=-Dlog4j.configuration=file:/opt/spark/conf/log4j.properties \
    local:///opt/spark/examples/jars/app.jar bootstrap.kafka.svc.cluster.local:9092 1 meetup 20 1 1
