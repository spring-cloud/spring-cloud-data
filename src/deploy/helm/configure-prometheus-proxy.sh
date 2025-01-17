#!/usr/bin/env bash
if [ "$2" = "" ]; then
    echo "Usage is: <host> <port> [step]"
    echo "Where <step> is the frequency of published metrics. Default is 10s"
    exit 1
fi
export HOST=$1
export PORT=$2
if [ "$3" != "" ]; then
    STEP=$3
else
    STEP=10s
fi
export PROMETHEUS_URL="http://$HOST:$PORT"
yq "global.management.metrics.export.prometheus.rsocket.host = strenv(HOST)" -i ./scdf-helm-values.yml
yq "global.management.metrics.export.prometheus.pushgateway.base-url = strenv(PROMETHEUS_URL)" -i ./scdf-helm-values.yml
yq "global.management.metrics.export.prometheus.pushgateway.enabled = true" -i ./scdf-helm-values.yml
yq "global.management.metrics.export.prometheus.pushgateway.shutdown-operation = \"PUSH\"" -i ./scdf-helm-values.yml
yq "global.management.metrics.export.prometheus.step = \"$STEP\"" -i ./scdf-helm-values.yml
