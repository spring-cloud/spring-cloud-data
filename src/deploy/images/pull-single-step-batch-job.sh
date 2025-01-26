#!/bin/bash
if [ "$DATAFLOW_VERSION" = "" ]; then
  DATAFLOW_VERSION=3.0.0-SNAPSHOT
fi
docker pull "springcloud/spring-cloud-dataflow-single-step-batch-job:$DATAFLOW_VERSION"
