#!/usr/bin/env bash

VERSION=0.14
REPO=berkgokden

sbt clean compile assembly

docker build -t ${REPO}/spark:${VERSION}  -f Dockerfile .

# After build push
docker push ${REPO}/spark:${VERSION}
