#!/bin/bash
if [ "$SKIPPER_VERSION" = "" ]; then
  SKIPPER_VERSION=2.11.1-SNAPSHOT
fi

docker pull "springcloud/spring-cloud-skipper-server:$SKIPPER_VERSION"
