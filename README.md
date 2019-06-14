# MeetUp Hype

MeetUp Hype is an Apache Spark Streaming App that works with Kafka Streams 
to calculate and show trending topics.

## building

If you change anything in the code you can build the application with running:

```
./build.sh
```
Don't forget to change REPO to a repository that you have write access

## running

This is tested on a kubernetes cluster so running will be explained for kubernetes.
It should run on any cluster with proper settings.
You may want to edit checkpoint directory depending on your cluster.
If you use YARN, you also may need to check the dependencies.

Prerequisites:
- A kubernetes cluster (Recommended 2CPU 8GB Memory and 5 Nodes)
- Kubectl locally installed and authenticated to the cluster
- Kafka setup, it will be explained later
- Kafka producer should be started, it will be explained later
- namespace should be created, in the examples namespace apps is used
- role bindings should be created for the namespace

This is the command I used for running the app.
You need to update kubernetes ip.
You can get kubernetes ip by running 'kubectl cluster-info'
Please read all the readme before running it.

```
bin/spark-submit \
    --master k8s://https://kubernetes_ip:443 \
    --deploy-mode cluster \
    --name spark-meetup \
    --class com.berkgokden.MeetupHype \
    --conf spark.executor.instances=5 \
    --conf spark.kubernetes.namespace=apps \
    --conf spark.kubernetes.container.image=berkgokden/spark:0.13 \
    --conf spark.driver.extraJavaOptions=-Dlog4j.configuration=file:/opt/spark/conf/log4j.properties \
    --conf spark.executor.extraJavaOptions=-Dlog4j.configuration=file:/opt/spark/conf/log4j.properties \
    local:///opt/spark/examples/jars/app.jar bootstrap.kafka.svc.cluster.local:9092 1 meetup 20 1 1
```

## Installing Kafka

Follow this to github project to install Kafka:
[kubernetes Kafka](https://github.com/Yolean/kubernetes-kafka)

Since kafka requires persistent volumes it is not easy to install a standard Kafka.
Requirements may change depending on your needs.


## Installing Kafka Producer

In kafkaproducer folder there is a small python project 
to read meetup streaming rest api and pushing to kafka
It can be build by running build.sh same as this project.

There is also kproducter.yaml to deploy it into the cluster.
Run:
```
kubectl apply -f kafkaproducer/kproducer.yaml
```

## Service account for Spark

You need your a service account with the corrent role binding for your namespace
For the apps namespace an example yaml is available in sa.yaml
Please note that a service account named default is created per namespace automatically
Spark expects the default service account to have correct role binding

Run:
```
kubectl apply -f sa.yaml
```

## Downloading Apache Spark binary

[Apache Spark Download](https://spark.apache.org/downloads.html)

Please download 2.3.2 for this project
I have run all the spark-submit commands in the root folder on
this project, or you can add it to the environment PATH

This project also allows you to build spark images.

```
./bin/docker-image-tool.sh -r <repo> -t my-tag build
./bin/docker-image-tool.sh -r <repo> -t my-tag push
```

Base image is created by running these commands:
```
./bin/docker-image-tool.sh -r bergokden -t base build
./bin/docker-image-tool.sh -r bergokden -t base push
```

Please not that image name is set to spark by default

## Checking the logs for output

For these examples namespace app is used.
Apache Spark creates and maneged pods instead of deployment.
To get the list of deployments:
```
kubectl get pods -n apps
```
Example output:
```
NAME                                                   READY     STATUS    RESTARTS   AGE
producer-7d9b47448d-dc9lc                              1/1       Running   0          27m
spark-meetup-e658b60033683648894b44803ea889ff-driver   1/1       Running   0          27m
spark-meetup-e658b60033683648894b44803ea889ff-exec-1   1/1       Running   0          21m
spark-meetup-e658b60033683648894b44803ea889ff-exec-2   1/1       Running   0          21m
spark-meetup-e658b60033683648894b44803ea889ff-exec-3   1/1       Running   0          21m
spark-meetup-e658b60033683648894b44803ea889ff-exec-4   1/1       Running   0          21m
spark-meetup-e658b60033683648894b44803ea889ff-exec-5   1/1       Running   0          21m
```

The output of the driver is important for us.
PS: In this project logs are turned off. It can be set back to onin ./src/main/resources/log4j.properties

To check the logs use the driver pod, run:
```
kubectl logs -f -n apps spark-meetup-ebe14872caed370391a8a2ec2a7dfc3d-driver
```
Example output:
```

-------------------------------------------
Time: 1538491200000 ms
-------------------------------------------
Open Source
Fitness
Hiking
English as a Second Language
Software Development
Entrepreneurship
Social Networking
Small Business
Social
Self-Improvement
Web Design
Badminton
Tokyo
Artificial Intelligence
Dining Out
New Technology
yokohama
New In Town
Singles
Meditation
```

## Runtime parameters

Example parameters used for testing:
```
bootstrap.kafka.svc.cluster.local:9092 1 meetup 20 1 1
```

Explained in the order of parameters:

* "bootstrap.kafka.svc.cluster.local:9092" is the boostreap url for the cluster
It should be the same if you are using the same set up

* "1" is for the kafka group, it is the same for this set up.

* "meetup" is the name of the kafka topic

* "20" is the number of trending of topics to see

* "1" is the number of days to consider. 
Please note that if a topic does not get any positive rsvp in last 70 minutes,
it will be removed from the final list.

* "1" this is the decay factor.
If you increase decay factor, old values will get less importance.
If decay factor is 0, it basically returns most popular topics in last N days.


## About Architecture

Rest Api => Kafka Producer => Kafka => Apache Spark Streaming App => Output

This a very common data streaming architecture, all parts will be explained one by one:

* Rest Api is the source where Meetup Rest Api

* Kafka Producer is the python app that reads meetup Rest Api as Stream and push messages to Kafka.
This app can be scaled to multiple instances if the stream reading is slow.
If same rsvp is read multiple times, it will be handled inside data processing phase.

* Kafka is the Kafka cluster with Zookeeper. Kafka manages the message queue in a distributed way.
It has a reliable message queue with at least once reliability.
Kafka is needed in the environments, when there is a high volume message stream.
In other cases, it can be also used if input digestion can be separated from data processing, like this case

* Apache Spark Streaming App is the app build in this project.
This app has integration to the kafka and runs the defined stream in a distributed process

* Output is current the console output in the driver 
but in production it should be another service that caches 
and shows the result to a larger audience

# meetuphype
