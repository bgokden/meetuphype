FROM berkgokden/spark:base

COPY ./target/app.jar /opt/spark/examples/jars/

COPY ./src/main/resources/log4j.properties /opt/spark/conf