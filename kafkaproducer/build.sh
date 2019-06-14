#!/usr/bin/env bash

VERSION=0.1
REPO=berkgokden

docker build -t ${REPO}/kproducer:${VERSION}  -f Dockerfile .

# After build push
docker push ${REPO}/kproducer:${VERSION}
