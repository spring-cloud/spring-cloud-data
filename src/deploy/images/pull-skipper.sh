#!/bin/bash
if [ "$SKIPPER_VERSION" = "" ]; then
  SKIPPER_VERSION=3.0.0-SNAPSHOT
fi

docker pull "springcloud/spring-cloud-skipper-server:$SKIPPER_VERSION"
